// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;


public interface ReferenceImporter {
  ExtensionPointName<ReferenceImporter> EP_NAME = ExtensionPointName.create("com.intellij.referenceImporter");

  /**
   * @return true if this extension found an unresolved reference at the current caret offset and successfully imported it.
   * (for example, in case of Java, the plugin added the corresponding import statement to this file, thus making this reference valid)
   */
  default boolean autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    ThreadingAssertions.assertEventDispatchThread();
    Future<BooleanSupplier> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> {
      if (editor.isDisposed() || psiFile.getProject().isDisposed()) return null;
      int offset = editor.getCaretModel().getOffset();
      return computeAutoImportAtOffset(editor, psiFile, offset, true);
    }));
    try {
      BooleanSupplier fix = future.get();
      if (fix != null) {
        return fix.getAsBoolean();
      }
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    return false;
  }

  /**
   * This method is called in the background to compute whether this {@code offset} contain an unresolved reference which can be auto-imported
   * (e.g., by adding the corresponding import statement to the file).
   * When this extension found an auto-importable reference, it should return an action which, being run in the EDT, does the auto-import
   * and returns true if the auto-import completed successfully or false if auto-import wasn't able to run.
   * This method allows to split the process of importing the reference into two parts: computing in background and applying in EDT,
   * to reduce freezes and improve responsiveness.
   */
  @ApiStatus.Experimental
  default BooleanSupplier computeAutoImportAtOffset(@NotNull Editor editor, @NotNull PsiFile psiFile, int offset, boolean allowCaretNearReference) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    return null;
  }

  /**
   * Checks whether the IDE should try to add imports for unresolved references automatically.
   * Override in your language plugin when it provided adding new imports for unresolved references.
   * (like, for example, Java supports adding "import java.util.ArrayList" for unresolved reference "ArrayList list = null;")
   * @return true if the {@code file} is of type your plugin supports (i.e. {@link #autoImportReferenceAtCursor} will work for this file)
   * and the "add imports on the fly" feature is enabled for this file (e.g., "Settings|Auto Imports|Java|Add imports on the fly" option is on for the Java files).
   */
  default boolean isAddUnambiguousImportsOnTheFlyEnabled(@NotNull PsiFile psiFile) {
    return false;
  }
}
