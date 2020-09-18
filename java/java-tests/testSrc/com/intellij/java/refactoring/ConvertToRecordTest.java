// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.convertToRecord.ConvertToRecordHandler;
import com.intellij.refactoring.convertToRecord.ConvertToRecordProcessor;
import com.intellij.testFramework.TestDataPath;

/**
 * @author ven
 */
@TestDataPath("$CONTENT_ROOT/testData/refactoring/convertToRecord/")
public class ConvertToRecordTest extends LightRefactoringParameterizedTestCase {
  @Override
  protected void perform() {
    PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiClass);
    var definition = ConvertToRecordHandler.getClassDefinition((PsiClass)element);
    if (definition != null) {
      new ConvertToRecordProcessor(definition, false).run();
    }
  }

  @Override
  protected String getAfterFile(String fileName) {
    return FileUtilRt.getNameWithoutExtension(fileName) + "_" + AFTER_PREFIX + "." + FileUtilRt.getExtension(fileName);
  }

  @Override
  protected String getBeforeFile(String fileName) {
    return fileName;
  }

  @Override
  public String getFileSuffix(String beforeFile) {
    return !beforeFile.contains(AFTER_PREFIX) && !beforeFile.endsWith(CONFLICTS_SUFFIX) ? beforeFile : null;
  }
}
