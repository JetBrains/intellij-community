/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 *
 * @see IdeaTestFixtureFactory#createCodeInsightFixture(IdeaProjectTestFixture)
 *
 * @author Dmitry Avdeev
 */
public interface CodeInsightTestFixture extends IdeaTestFixture {

  @NonNls String CARET_MARKER = "<caret>";
  @NonNls String SELECTION_START_MARKER = "<selection>";
  @NonNls String SELECTION_END_MARKER = "</selection>";

  @NonNls String ERROR_MARKER = "error";
  @NonNls String WARNING_MARKER = "warning";
  @NonNls String INFORMATION_MARKER = "weak_warning";
  @NonNls String SERVER_PROBLEM_MARKER = "server_problem";
  @NonNls String INFO_MARKER = "info";
  @NonNls String END_LINE_HIGHLIGHT_MARKER = "EOLError";
  @NonNls String END_LINE_WARNING_MARKER = "EOLWarning";

  Project getProject();

  Module getModule();

  Editor getEditor();
  
  PsiFile getFile();

  void setTestDataPath(@NonNls String dataPath);

  String getTempDirPath();

  TempDirTestFixture getTempDirFixture();

  VirtualFile copyFileToProject(@NonNls String sourceFilePath, @NonNls String targetPath) throws IOException;

  VirtualFile copyFileToProject(@NonNls String sourceFilePath) throws IOException;

  /**
   * Enables inspections for highlighting tests.
   * Should be called BEFORE {@link #setUp()}.
   *
   * @param inspections inspections to be enabled in highliting tests.
   * @see #enableInspections(com.intellij.codeInspection.InspectionToolProvider[])
   */
  void enableInspections(LocalInspectionTool... inspections);

  void enableInspections(Class<? extends LocalInspectionTool>... inspections);

  void disableInspections(LocalInspectionTool... inspections);

  /**
   * Enable all inspections provided by given providers.
   *
   * @param providers providers to be enabled.
   * @see #enableInspections(com.intellij.codeInspection.LocalInspectionTool[])
   */
  void enableInspections(InspectionToolProvider... providers);

  /**
   * Runs highliting test for the given files.
   * Checks for {@link #ERROR_MARKER} markers by default.
   *
   * @param checkWarnings enables {@link #WARNING_MARKER} support.
   * @param checkInfos enables {@link #INFO_MARKER} support.
   * @param checkWeakWarnings enables {@link #INFORMATION_MARKER} support.
   * @param filePaths the first file is tested only; the others are just copied along the first.
   *
   * @return highlighting duration in milliseconds.
   * @throws Throwable any exception thrown during highlighting.
   */
  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @NonNls String... filePaths) throws Throwable;

  /**
   * Check highlighting of file already loaded by configure* methods
   * @param checkWarnings
   * @param checkInfos
   * @param checkWeakWarnings
   * @return
   * @throws Throwable
   */
  long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) throws Throwable;

  long checkHighlighting() throws Throwable;

  /**
   * Runs highliting test for the given files.
   * The same as {@link #testHighlighting(boolean, boolean, boolean, String...)} with all options set.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   *
   * @return highlighting duration in milliseconds
   * @throws Throwable any exception thrown during highlighting
   */
  long testHighlighting(@NonNls String... filePaths) throws Throwable;

  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, VirtualFile file) throws Throwable;
  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   *
   * @param filePaths
   * @return null if no reference found.
   * @throws Throwable any exception.
   *
   * @see #getReferenceAtCaretPositionWithAssertion(String...)
   */
  @Nullable
  PsiReference getReferenceAtCaretPosition(@NonNls String... filePaths) throws Throwable;

  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   * Asserts that the reference exists.
   *
   * @param filePaths
   * @return founded reference
   * @throws Throwable any exception
   *
   * @see #getReferenceAtCaretPosition(String...)
   */
  @NotNull
  PsiReference getReferenceAtCaretPositionWithAssertion(@NonNls String... filePaths) throws Throwable;

  /**
   * Collects available intentions in the whole file or at caret position if {@link #CARET_MARKER} presents.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   * @return available intentions.
   * @throws Throwable any exception.
   */
  @NotNull
  List<IntentionAction> getAvailableIntentions(@NonNls String... filePaths) throws Throwable;

  @NotNull
  List<IntentionAction> getAvailableIntentions() throws Throwable;

  /**
   * Launches the given action. Use {@link #checkResultByFile(String)} to check the result.
   *
   * @param action the action to be launched.
   * @throws Throwable any exception.
   */
  void launchAction(@NotNull IntentionAction action) throws Throwable;

  void configureByFile(@NonNls String file) throws Throwable;

  void configureByFiles(@NonNls String... files) throws Throwable;

  PsiFile configureByText(FileType fileType, @NonNls String text) throws Throwable;

  /**
   * Compares current file against the given one.
   *
   * @param expectedFile file to check against.
   * @throws Throwable any exception.
   */
  void checkResultByFile(@NonNls String expectedFile) throws Throwable;

  /**
   * Compares two files.
   *
   * @param filePath file to be checked.
   * @param expectedFile file to check against.
   * @param ignoreWhitespaces set to true to ignore differences in whitespaces.
   * @throws Throwable any exception.
   */
  void checkResultByFile(@NonNls String filePath, @NonNls String expectedFile, boolean ignoreWhitespaces) throws Throwable;

  void testCompletion(@NonNls String[] filesBefore, @NonNls String fileAfter) throws Throwable;

  /**
   * Runs basic completion in caret position in fileBefore.
   * Implies that there is only one completion variant and it was inserted automatically, and checks the result file text with fileAfter
   * @param fileBefore
   * @param fileAfter
   * @param additionalFiles
   * @throws Throwable
   */
  void testCompletion(@NonNls String fileBefore, @NonNls String fileAfter, final String... additionalFiles) throws Throwable;

  /**
   * Runs basic completion in caret position in fileBefore.
   * Checks that lookup is shown and it contains items with given lookup strings
   * @param fileBefore
   * @param items most probably will contain > 1 items
   * @throws Throwable
   */
  void testCompletionVariants(@NonNls String fileBefore, @NonNls String... items) throws Throwable;

  /**
   * Launches renaming refactoring and checks the result.
   *
   * @param fileBefore original file path. Use {@link #CARET_MARKER} to mark the element to rename.
   * @param fileAfter result file to be checked against.
   * @param newName new name for the element.
   * @param additionalFiles
   * @throws Throwable any exception.
   */
  void testRename(@NonNls String fileBefore, @NonNls String fileAfter, @NonNls String newName, final String... additionalFiles) throws Throwable;

  PsiReference[] testFindUsages(@NonNls String... fileNames) throws Throwable;

  void moveFile(@NonNls String filePath, @NonNls String to, final String... additionalFiles) throws Throwable;

  /**
   * Returns gutter renderer at the caret position.
   * Use {@link #CARET_MARKER} to mark the element to check.
   *
   * @param filePath file path
   * @return gutter renderer at the caret position.
   * @throws Throwable any exception.
   */
  @Nullable
  GutterIconRenderer findGutter(@NonNls String filePath) throws Throwable;

  PsiClass addClass(@NotNull @NonNls final String classText) throws IOException;

  PsiManager getPsiManager();

  @Nullable LookupElement[] completeBasic();

  void checkResult(final String text) throws IOException;

  Document getDocument(PsiFile file);

  void setFileContext(@Nullable PsiElement context);

  @NotNull
  Collection<GutterIconRenderer> findAllGutters(String filePath) throws Throwable;

  void testRename(String fileAfter, String newName) throws Throwable;

  void type(final char c);

  JavaPsiFacade getJavaFacade();

  int configureFromTempProjectFile(String filePath) throws IOException;

  void configureFromExistingVirtualFile(VirtualFile f) throws IOException;

  PsiFile addFileToProject(@NonNls String relativePath, @NonNls String fileText) throws IOException;

  List<String> getCompletionVariants(String fileBefore) throws Throwable;
}
