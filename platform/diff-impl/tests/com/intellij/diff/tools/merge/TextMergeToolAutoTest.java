/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.merge;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.DocumentContentImpl;
import com.intellij.diff.merge.MergeContext;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.TextMergeChange;
import com.intellij.diff.merge.TextMergeRequest;
import com.intellij.diff.merge.TextMergeTool.TextMergeViewer;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public class TextMergeToolAutoTest extends PlatformTestCase {
  protected final Random myRng = new Random();

  private static class TestSettings {
    public int maxLineLength = 2;
    public int maxLineCount = 100;
    public int charCount = 3;

    public int modificationCount = 30;
    public int maxModificationLength = 30;
  }

  public void testName() throws Exception {
    for (int i = 0; i < 100; i++) {
      //if (i % 10 == 0) System.out.println(i);
      doTest(new TestSettings());
    }
  }

  public void doTest(TestSettings settings) {
    Document outputDocument = generateDocument(settings);
    Document leftDocument = generateDocument(settings);
    Document rightDocument = generateDocument(settings);

    MyMergeContext context = new MyMergeContext();
    MyMergeRequest request = new MyMergeRequest(outputDocument, leftDocument, rightDocument);

    TextMergeViewer viewer = new TextMergeViewer(context, request);
    viewer.init();

    UIUtil.dispatchAllInvocationEvents(); // perform invokeLater in initial rediff

    try {
      performModifications(outputDocument, viewer, settings);
    } finally {
      Disposer.dispose(viewer);
    }
  }

  private Document generateDocument(TestSettings settings) {
    StringBuilder builder = new StringBuilder();

    int lineCount = myRng.nextInt(settings.maxLineCount);
    for (int i = 0; i < lineCount; i++) {
      int lineLength = myRng.nextInt(settings.maxLineLength);

      for (int j = 0; j < lineLength; j++) {
        int rnd = myRng.nextInt(settings.charCount);
        builder.append(getLabel(rnd));
      }
      builder.append('\n');
    }

    if (builder.length() > 0 && myRng.nextBoolean()) builder.deleteCharAt(builder.length() - 1);

    return new DocumentImpl(builder);
  }

  private void performModifications(final Document document, final TextMergeViewer viewer, final TestSettings settings) {
    for (int i = 0; i < settings.modificationCount; i++) {
      CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              switch (myRng.nextInt(2)) {
                case 0: // modify text
                  applyRandomChange(document, settings.maxModificationLength);
                  break;
                case 1: // apply modification
                  List<TextMergeChange> changes = viewer.getViewer().getChanges();
                  if (changes.isEmpty()) return;
                  TextMergeChange change = changes.get(myRng.nextInt(changes.size()));
                  Side side = Side.fromLeft(myRng.nextBoolean());
                  if (myRng.nextBoolean()) {
                    viewer.getViewer().replaceChange(change, side);
                  }
                  else {
                    viewer.getViewer().appendChange(change, side);
                  }
                  break;
              }
              int line = 0;
              for (TextMergeChange change : viewer.getViewer().getAllChanges()) {
                int startLine = change.getStartLine(ThreeSide.BASE);
                int endLine = change.getEndLine(ThreeSide.BASE);
                assertFalse(line > startLine);
                assertFalse(startLine > endLine);
                line = endLine;
              }
            }
          });
        }
      }, "", "");
    }
  }

  private void applyRandomChange(Document document, int changeLength) {
    int textLength = document.getTextLength();
    int type = myRng.nextInt(3);
    int offset = textLength != 0 ? myRng.nextInt(textLength) : 0;
    int length = textLength - offset != 0 ? myRng.nextInt(textLength - offset) : offset;
    String data = generateText(changeLength);
    //System.out.println("Change: " + type + " - " + offset + " - " + length + " - " + data.replace("\n", "\\n"));
    switch (type) {
      case 0: // insert
        document.insertString(offset, data);
        break;
      case 1: // delete
        document.deleteString(offset, offset + length);
        break;
      case 2: // modify
        document.replaceString(offset, offset + length, data);
        break;
    }
  }

  @NotNull
  private String generateText(int textLength) {
    int length = myRng.nextInt(textLength);
    StringBuilder builder = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      int rnd = myRng.nextInt(10);
      if (rnd == 0) {
        builder.append(' ');
      }
      else if (rnd < 7) {
        builder.append(String.valueOf(rnd));
      }
      else {
        builder.append('\n');
      }
    }

    return builder.toString();
  }

  protected static char getLabel(int i) {
    return (char)(i + 97);
  }

  //
  // Helpers
  //

  private static class MyMergeRequest extends TextMergeRequest {
    private final DocumentContent myOutput;
    private final List<DocumentContent> myContents;

    public MyMergeRequest(@NotNull Document output, @NotNull Document left, @NotNull Document right) {
      myOutput = new DocumentContentImpl(output);
      myContents = ContainerUtil.<DocumentContent>list(new DocumentContentImpl(left),
                                                       new DocumentContentImpl(output),
                                                       new DocumentContentImpl(right));
    }

    @NotNull
    @Override
    public List<DocumentContent> getContents() {
      return myContents;
    }

    @NotNull
    @Override
    public DocumentContent getOutputContent() {
      return myOutput;
    }

    @NotNull
    @Override
    public List<String> getContentTitles() {
      return ContainerUtil.list(null, null, null);
    }

    @Override
    public void applyResult(@NotNull MergeResult result) {
    }

    @Nullable
    @Override
    public String getTitle() {
      return null;
    }
  }

  private static class MyMergeContext extends MergeContext {
    @Nullable
    @Override
    public Project getProject() {
      return null;
    }

    @Override
    public boolean isFocused() {
      return true;
    }

    @Override
    public void requestFocus() {
    }

    @Override
    public void closeDialog() {
    }
  }
}
