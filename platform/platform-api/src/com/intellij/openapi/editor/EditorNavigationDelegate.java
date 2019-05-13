package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for extending editor navigation functionality. 
 * 
 * @author Denis Zhdanov
 */
public interface EditorNavigationDelegate {

  ExtensionPointName<EditorNavigationDelegate> EP_NAME = ExtensionPointName.create("com.intellij.editorNavigation");
  
  enum Result {
    /**
     * Navigation request is completely handled by the current delegate and no further processing is required.
     */
    STOP,

    /**
     * Continue navigation request processing.
     */
    CONTINUE
  }

  @NotNull
  Result navigateToLineEnd(@NotNull Editor editor, @NotNull DataContext dataContext);
}
