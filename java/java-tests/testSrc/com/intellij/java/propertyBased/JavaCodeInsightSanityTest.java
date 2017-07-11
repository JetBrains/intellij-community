/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.propertyBased;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import slowCheck.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * @author peter
 */
@SkipSlowTestLocally
public class JavaCodeInsightSanityTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testRandomIntentions() {
    AbstractApplyAndRevertTestCase.enableAllInspections(getProject(), getTestRootDisposable());
    FileFilter fileFilter = f -> f.getName().endsWith(".java");
    Generator<FileWithActions> actions = actionsOnFileContents(myFixture, PathManager.getHomePath(), fileFilter,
                                                               file -> Generator.anyOf(InvokeIntention.randomIntentions(file),
                                                                                       DeleteRange.deletePsiRange(file)));
    PropertyChecker.forAll(actions, FileWithActions::runActions);
  }

  @NotNull
  private static Generator<FileWithActions> actionsOnFileContents(CodeInsightTestFixture fixture, String rootPath,
                                                                  FileFilter fileFilter,
                                                                  Function<PsiFile, Generator<? extends MadTestingAction>> actions) {
    FileFilter childFilter = child -> {
      String name = child.getName();
      if (name.startsWith(".")) return false;

      return child.isDirectory() ? shouldGoInsiderDir(name)
                                 : fileFilter.accept(child) && child.length() < 500_000;
    };
    Generator<File> randomFiles =
      Generator.from(new FileGenerator(new File(rootPath), childFilter)).suchThat(Objects::nonNull).noShrink();
    return randomFiles.flatMap(ioFile -> {
      PsiFile file = copyFileToProject(ioFile, fixture, rootPath);
      if (file == null || file instanceof PsiBinaryFile || file.getTextLength() == 0) {
        return Generator.constant(new FileWithActions(file, Collections.emptyList()));
      }
      return Generator.nonEmptyLists(actions.apply(file)).map(a -> new FileWithActions(file, a));
    });
  }

  private static boolean shouldGoInsiderDir(@NotNull String name) {
    return !name.equals("out") &&
           !name.equals("gen") && // https://youtrack.jetbrains.com/issue/IDEA-175404
           !name.endsWith("system") && !name.endsWith("config");
  }

  @NotNull
  private static PsiFile copyFileToProject(File ioFile, CodeInsightTestFixture fixture, String rootPath) {
    //todo strip test data markup
    try {
      String path = FileUtil.getRelativePath(rootPath, ioFile.getPath(), '/');
      return fixture.addFileToProject(path, FileUtil.loadFile(ioFile));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class FileGenerator implements Function<DataStructure, File> {
    private final File myRoot;
    private final FileFilter myFilter;

    public FileGenerator(File root, FileFilter filter) {
      myRoot = root;
      myFilter = filter;
    }

    @Override
    public File apply(DataStructure data) {
      return generateRandomFile(data, myRoot, new HashSet<>());
    }

    @Nullable
    private File generateRandomFile(DataStructure data, File file, Set<File> exhausted) {
      while (true) {
        File[] children = file.listFiles(f -> !exhausted.contains(f) && myFilter.accept(f));
        if (children == null) {
          return file;
        }
        if (children.length == 0) {
          exhausted.add(file);
          return null;
        }

        List<File> toChoose = preferDirs(data, children);
        int index = data.drawInt(IntDistribution.uniform(0, toChoose.size() - 1));
        File generated = generateRandomFile(data, toChoose.get(index), exhausted);
        if (generated != null) {
          return generated;
        }
      }
    }

    private static List<File> preferDirs(DataStructure data, File[] children) {
      List<File> files = new ArrayList<>();
      List<File> dirs = new ArrayList<>();
      for (File child : children) {
        (child.isDirectory() ? dirs : files).add(child);
      }

      if (files.isEmpty() || dirs.isEmpty()) {
        return Arrays.asList(children);
      }

      int ratio = Math.max(100, dirs.size() / files.size());
      return data.drawInt() % ratio != 0 ? dirs : files;
    }
  }

}
