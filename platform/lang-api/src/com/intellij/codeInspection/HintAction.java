package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;

/**
 * @author Dmitry Avdeev
 */
public interface HintAction extends IntentionAction {

  boolean showHint(final Editor editor);  
}
