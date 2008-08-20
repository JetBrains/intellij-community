package com.intellij.openapi.actionSystem;

import com.intellij.ide.IdeView;
import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public class LangDataKeys extends PlatformDataKeys {
  public static final DataKey<Module> MODULE = DataKey.create(DataConstants.MODULE);
  public static final DataKey<Module> MODULE_CONTEXT = DataKey.create(DataConstants.MODULE_CONTEXT);
  public static final DataKey<Module[]> MODULE_CONTEXT_ARRAY = DataKey.create(DataConstants.MODULE_CONTEXT_ARRAY);
  public static final DataKey<PsiElement> PSI_ELEMENT = DataKey.create(DataConstants.PSI_ELEMENT);
  public static final DataKey<PsiFile> PSI_FILE = DataKey.create(DataConstants.PSI_FILE);
  public static final DataKey<Language> LANGUAGE = DataKey.create(DataConstants.LANGUAGE);
  public static final DataKey<PsiElement[]> PSI_ELEMENT_ARRAY = DataKey.create(DataConstants.PSI_ELEMENT_ARRAY);
  public static final DataKey<IdeView> IDE_VIEW = DataKey.create(DataConstants.IDE_VIEW);
  public static final DataKey<Condition<AnAction>> PRESELECT_NEW_ACTION_CONDITION = DataKey.create("newElementAction.preselect.id");
}
