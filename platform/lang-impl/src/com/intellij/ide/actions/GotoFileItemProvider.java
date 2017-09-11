/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.ide.util.gotoByName.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author peter
*/
public class GotoFileItemProvider extends DefaultChooseByNameItemProvider {
  private final Project myProject;
  private final GotoFileModel myModel;

  public GotoFileItemProvider(@NotNull Project project, @Nullable PsiElement context, GotoFileModel model) {
    super(context);
    myProject = project;
    myModel = model;
  }

  @Override
  public boolean filterElements(@NotNull ChooseByNameBase base,
                                @NotNull String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator indicator,
                                @NotNull Processor<Object> consumer) {
    PsiFileSystemItem absolute = getFileByAbsolutePath(pattern);
    if (absolute != null && !consumer.process(absolute)) {
      return true;
    }


    if (pattern.startsWith("./") || pattern.startsWith(".\\")) {
      pattern = pattern.substring(1);
    }

    String sanitized = removeSlashes(base.transformPattern(pattern));
    List<List<String>> nameMatches = getFileNameCandidates(base, everywhere, sanitized, !pattern.startsWith("*"));

    MinusculeMatcher fullMatcher = NameUtil.buildMatcher("*" + sanitized, NameUtil.MatchingCaseSensitivity.NONE);
    PathProximityComparator pathProximityComparator = getPathProximityComparator();

    for (List<String> group : nameMatches) {
      if (!ContainerUtil.process(getFilesMatchingPath(pattern, everywhere, fullMatcher, pathProximityComparator, group), consumer)) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  private static String removeSlashes(String s) {
    if (s.startsWith("/") || s.startsWith("\\")) return removeSlashes(s.substring(1));
    if (s.endsWith("/") || s.endsWith("\\")) return removeSlashes(s.substring(0, s.length() - 1));
    return s;
  }

  @Nullable
  private PsiFileSystemItem getFileByAbsolutePath(@NotNull String pattern) {
    if (pattern.contains("/") || pattern.contains("\\")) {
      String path = FileUtil.toSystemIndependentName(ChooseByNamePopup.getTransformedPattern(pattern, myModel));
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPathIfCached(path);
      if (vFile != null) {
        ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(myProject);
        if (index.isInContent(vFile) || index.isInLibraryClasses(vFile) || index.isInLibrarySource(vFile)) {
          return vFile.isDirectory() ? PsiManager.getInstance(myProject).findDirectory(vFile) : PsiManager.getInstance(myProject).findFile(vFile);
        }
      }
    }
    return null;
  }

  @NotNull
  private List<PsiFileSystemItem> getFilesMatchingPath(@NotNull String pattern,
                                                       boolean everywhere,
                                                       MinusculeMatcher fullMatcher,
                                                       PathProximityComparator pathProximityComparator, List<String> fileNames) {
    List<PsiFileSystemItem> group = new ArrayList<>();
    Map<PsiFileSystemItem, Integer> qualifierMatchingDegrees = new HashMap<>();
    Map<PsiFileSystemItem, Integer> dirCloseness = new HashMap<>();
    Map<PsiFileSystemItem, Integer> nesting = new HashMap<>();
    for (String fileName : fileNames) {
      for (Object o : myModel.getElementsByName(fileName, everywhere, pattern)) {
        String fullName = myModel.getFullName(o);
        if (o instanceof PsiFileSystemItem && fullName != null) {
          FList<TextRange> fragments = fullMatcher.matchingFragments(fullName);
          if (fragments != null) {
            group.add((PsiFileSystemItem)o);

            qualifierMatchingDegrees.put((PsiFileSystemItem)o, -fullMatcher.matchingDegree(fullName, false, fragments));

            String matchingArea = fullName.substring(fragments.getHead().getStartOffset(), fragments.get(fragments.size() - 1).getEndOffset());
            dirCloseness.put((PsiFileSystemItem)o, StringUtil.countChars(matchingArea, '/'));

            nesting.put((PsiFileSystemItem)o, StringUtil.countChars(fullName, '/'));
          }
        }
      }
    }

    if (group.size() > 1) {
      Collections.sort(group, Comparator.comparing(nesting::get).thenComparing(dirCloseness::get).thenComparing(qualifierMatchingDegrees::get).thenComparing(pathProximityComparator).thenComparing(myModel::getFullName));
    }
    return group;
  }

  @NotNull
  private List<List<String>> getFileNameCandidates(@NotNull ChooseByNameBase base,
                                                   boolean everywhere,
                                                   String sanitized, boolean preferStartMatches) {
    String[] names = myModel.getNames(everywhere);

    int start = Math.max(sanitized.lastIndexOf('/'), sanitized.lastIndexOf('\\')) + 1;

    Set<String> checkedNames = new HashSet<>();
    List<List<String>> groups = new ArrayList<>();
    for (int i = start; i < sanitized.length() - 1; i++) {
      List<MatchResult> nameMatches = new ArrayList<>();
      String namePattern = sanitized.substring(i);
      MinusculeMatcher matcher = NameUtil.buildMatcher(namePattern, NameUtil.MatchingCaseSensitivity.NONE);
      for (String name : names) {
        if (!checkedNames.contains(name)) {
          MatchResult result = matches(base, namePattern, matcher, name);
          if (result != null) {
            checkedNames.add(name);
            nameMatches.add(result);
          }
        }
      }
      groups.addAll(groupByMatchingDegree(nameMatches, namePattern, preferStartMatches));
    }

    return groups;
  }

  private static List<List<String>> groupByMatchingDegree(List<MatchResult> nameMatches,
                                                          String namePattern, boolean preferStartMatches) {
    if (nameMatches.isEmpty()) return Collections.emptyList();

    List<List<String>> groups = new ArrayList<>();

    Comparator<MatchResult> comparator = (mr1, mr2) -> {
      boolean exactPrefix1 = namePattern.equalsIgnoreCase(mr1.elementName);
      boolean exactPrefix2 = namePattern.equalsIgnoreCase(mr2.elementName);
      if (exactPrefix1 != exactPrefix2) return exactPrefix1 ? -1 : 1;
      return mr1.compareDegrees(mr2, preferStartMatches);
    };
    Collections.sort(nameMatches, comparator);

    List<String> group = ContainerUtil.newArrayList(nameMatches.get(0).elementName);
    for (int j = 1; j < nameMatches.size(); j++) {
      MatchResult current = nameMatches.get(j);
      if (comparator.compare(nameMatches.get(j - 1), current) == 0) {
        group.add(current.elementName);
      } else {
        groups.add(group);
        group = ContainerUtil.newArrayList(current.elementName);
      }
    }
    groups.add(group);
    return groups;
  }
}
