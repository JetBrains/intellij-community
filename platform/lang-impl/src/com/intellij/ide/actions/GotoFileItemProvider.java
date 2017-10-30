// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.util.gotoByName.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.FixingLayoutMatcher;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.indexing.FindSymbolParameters;
import one.util.streamex.IntStreamEx;
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

    if (!processItemsForPattern(base, pattern, everywhere, consumer, indicator)) {
      return false;
    }
    String fixed = FixingLayoutMatcher.fixLayout(pattern);
    return fixed == null || processItemsForPattern(base, fixed, everywhere, consumer, indicator);
  }

  private boolean processItemsForPattern(@NotNull ChooseByNameBase base,
                                         @NotNull String pattern,
                                         boolean everywhere,
                                         @NotNull Processor<Object> consumer,
                                         @NotNull ProgressIndicator indicator) {
    String sanitized = getSanitizedPattern(pattern, myModel);
    int qualifierEnd = sanitized.lastIndexOf('/') + 1;
    NameGrouper grouper = new NameGrouper(sanitized.substring(qualifierEnd), indicator);
    myModel.processNames(grouper::processName, true);

    Ref<Boolean> hasSuggestions = Ref.create(false);
    DirectoryPathMatcher dirMatcher = DirectoryPathMatcher.root(myModel, sanitized.substring(0, qualifierEnd));
    while (dirMatcher != null) {
      int index = grouper.index;
      SuffixMatches group = grouper.nextGroup(base);
      if (group == null) break;
      if (!group.processFiles(pattern, sanitized, everywhere, consumer, hasSuggestions, dirMatcher)) {
        return false;
      }
      dirMatcher = dirMatcher.appendChar(grouper.namePattern.charAt(index));
      if (!myModel.isSlashlessMatchingEnabled()) {
        return true;
      }
    }
    return true;
  }

  @NotNull
  public static String getSanitizedPattern(@NotNull String pattern, GotoFileModel model) {
    return removeSlashes(StringUtil.replace(ChooseByNamePopup.getTransformedPattern(pattern, model), "\\", "/"));
  }

  @NotNull
  public static MinusculeMatcher getQualifiedNameMatcher(@NotNull String pattern) {
    return NameUtil.buildMatcher("*" + StringUtil.replace(StringUtil.replace(pattern, "\\", "*\\*"), "/", "*/*"), NameUtil.MatchingCaseSensitivity.NONE);
  }

  @NotNull
  private static String removeSlashes(String s) {
    if (s.startsWith("/")) return removeSlashes(s.substring(1));
    if (s.endsWith("/")) return removeSlashes(s.substring(0, s.length() - 1));
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
                                                       List<String> fileNames,
                                                       DirectoryPathMatcher dirMatcher,
                                                       @NotNull ProgressIndicator indicator) {
    GlobalSearchScope scope = dirMatcher.narrowDown(FindSymbolParameters.searchScopeFor(myProject, everywhere));
    FindSymbolParameters parameters = new FindSymbolParameters(pattern, pattern, scope, null);

    List<PsiFileSystemItem> group = new ArrayList<>();
    Map<PsiFileSystemItem, Integer> qualifierMatchingDegrees = new HashMap<>();
    Map<PsiFileSystemItem, Integer> dirCloseness = new HashMap<>();
    Map<PsiFileSystemItem, Integer> nesting = new HashMap<>();
    for (String fileName : fileNames) {
      ProgressManager.checkCanceled();
      for (Object o : myModel.getElementsByName(fileName, parameters, indicator)) {
        ProgressManager.checkCanceled();
        String fullName = myModel.getFullName(o);
        if (o instanceof PsiFileSystemItem && fullName != null) {
          fullName = FileUtilRt.toSystemIndependentName(fullName);
          FList<TextRange> fragments = fullMatcher.matchingFragments(fullName);
          if (fragments != null && !fragments.isEmpty()) {
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
      Collections.sort(group,
                       Comparator.<PsiFileSystemItem, Boolean>comparing(f -> f instanceof PsiDirectory).
                         thenComparing(qualifierMatchingDegrees::get).
                         thenComparing(i -> i.getName().toLowerCase()).
                         thenComparing(nesting::get).
                         thenComparing(dirCloseness::get).
                         thenComparing(getPathProximityComparator()).
                         thenComparing(myModel::getFullName));
    }
    return group;
  }

  /**
   * @return Minimal {@code pos} such that {@code candidateName} can potentially match {@code namePattern.substring(pos)}
   * (i.e. contains the same letters as a sub-sequence).
   * Matching attempts with longer pattern substrings certainly will fail.
   */
  private static int findMatchStartingPosition(String candidateName, String namePattern) {
    int namePos = candidateName.length();
    for (int i = namePattern.length(); i > 0; i--) {
      char c = namePattern.charAt(i - 1);
      if (Character.isLetterOrDigit(c)) {
        namePos = StringUtil.lastIndexOfIgnoreCase(candidateName, c, namePos - 1);
        if (namePos < 0) {
          return i;
        }
      }
    }
    return 0;
  }

  private class NameGrouper {
    private final String namePattern;
    @NotNull private final ProgressIndicator indicator;
    
    /** Names placed into buckets where the index of bucket == {@link #findMatchStartingPosition} */
    private final List<List<String>> candidateNames;

    private int index = 0;

    NameGrouper(@NotNull String namePattern, @NotNull ProgressIndicator indicator) {
      this.namePattern = namePattern;
      candidateNames = IntStreamEx.range(0, namePattern.length()).mapToObj(__ -> (List<String>)new ArrayList<String>()).toList();
      this.indicator = indicator;
    }

    boolean processName(String name) {
      ProgressManager.checkCanceled();
      int position = findMatchStartingPosition(name, namePattern);
      if (position < namePattern.length()) {
        List<String> list = candidateNames.get(position);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (list) { // names can be processed concurrently
          list.add(name);
        }
      }
      return true;
    }

    @Nullable
    SuffixMatches nextGroup(ChooseByNameBase base) {
      if (index >= namePattern.length()) return null;
      
      SuffixMatches matches = new SuffixMatches(namePattern.substring(index), indicator);
      for (String name : candidateNames.get(index)) {
        if (!matches.matchName(base, name) && index + 1 < namePattern.length()) {
          candidateNames.get(index + 1).add(name); // try later with a shorter matcher
        }
      }
      candidateNames.set(index, null);
      index++;
      return matches;
    }
  }

  private class SuffixMatches {
    final String patternSuffix;
    final MinusculeMatcher matcher;
    final List<MatchResult> matchingNames = new ArrayList<>();
    final ProgressIndicator indicator;

    SuffixMatches(String patternSuffix, @NotNull ProgressIndicator indicator) {
      this.patternSuffix = patternSuffix;
      matcher = NameUtil.buildMatcher("*" + patternSuffix, NameUtil.MatchingCaseSensitivity.NONE);
      this.indicator = indicator;
    }

    @Override
    public String toString() {
      return "SuffixMatches{" +
             "patternSuffix='" + patternSuffix + '\'' +
             ", matchingNames=" + matchingNames +
             '}';
    }

    boolean matchName(@NotNull ChooseByNameBase base, String name) {
      MatchResult result = matches(base, patternSuffix, matcher, name);
      if (result != null) {
        matchingNames.add(result);
        return true;
      }
      return false;
    }

    boolean processFiles(@NotNull String pattern,
                         String sanitizedPattern,
                         boolean everywhere,
                         Processor<? super PsiFileSystemItem> processor,
                         Ref<Boolean> hasSuggestions,
                         DirectoryPathMatcher dirMatcher) {
      MinusculeMatcher fullMatcher = getQualifiedNameMatcher(sanitizedPattern);

      List<List<String>> groups = groupByMatchingDegree(!pattern.startsWith("*"));
      for (List<String> group : groups) {
        List<PsiFileSystemItem> files = getFilesMatchingPath(pattern, everywhere, fullMatcher, group, dirMatcher, indicator);
        if (!files.isEmpty()) {
          hasSuggestions.set(true);
        }
        if (!ContainerUtil.process(files, processor)) {
          return false;
        }
      }

      if (!hasSuggestions.get() && !everywhere && hasSuggestionsOutsideProject(pattern, fullMatcher, groups, dirMatcher)) {
        // let the framework switch to searching outside project to display these well-matching suggestions
        // instead of worse-matching ones in project (that are very expensive to calculate)
        return false;
      }
      return true;
    }

    private boolean hasSuggestionsOutsideProject(@NotNull String pattern,
                                                 MinusculeMatcher fullMatcher,
                                                 List<List<String>> groups, DirectoryPathMatcher dirMatcher) {
      return ContainerUtil.exists(groups, group -> !getFilesMatchingPath(pattern, true, fullMatcher, group, dirMatcher, indicator).isEmpty());
    }

    private List<List<String>> groupByMatchingDegree(boolean preferStartMatches) {
      if (matchingNames.isEmpty()) return Collections.emptyList();

      List<List<String>> groups = new ArrayList<>();

      Comparator<MatchResult> comparator = (mr1, mr2) -> {
        boolean exactPrefix1 = StringUtil.startsWith(mr1.elementName, patternSuffix);
        boolean exactPrefix2 = StringUtil.startsWith(mr2.elementName, patternSuffix);
        if (exactPrefix1 && exactPrefix2) return 0;
        if (exactPrefix1 != exactPrefix2) return exactPrefix1 ? -1 : 1;
        return mr1.compareDegrees(mr2, preferStartMatches);
      };
      Collections.sort(matchingNames, comparator);

      List<String> group = ContainerUtil.newArrayList(matchingNames.get(0).elementName);
      for (int j = 1; j < matchingNames.size(); j++) {
        MatchResult current = matchingNames.get(j);
        if (comparator.compare(matchingNames.get(j - 1), current) == 0) {
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
}
