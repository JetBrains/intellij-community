/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.actions;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * @author spleaner
 */
public class CreateXhtmlAction extends CreateHtmlAction {

  public CreateXhtmlAction() {
    super(StdFileTypes.XHTML, FileTemplateManager.INTERNAL_XHTML_TEMPLATE_NAME);
  }
}
