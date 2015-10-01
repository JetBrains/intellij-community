
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NonNls;

@SuppressWarnings({"HardCodedStringLiteral"})
public class HelpID {
  public static final String RENAME_PACKAGE   = "refactoring.renamePackage";
  public static final String RENAME_CLASS     = "refactoring.renameClass";
  public static final String RENAME_METHOD    = "refactoring.renameMethod";
  public static final String RENAME_FIELD     = "refactoring.renameField";
  public static final String RENAME_VARIABLE  = "refactoring.renameVariable";
  public static final String RENAME_PARAMETER = "refactoring.renameParameter";
  public static final String RENAME_DIRECTORY = "refactoring.renameDirectory";
  public static final String RENAME_FILE      = "refactoring.renameFile";

  private static final String MOVE_PACKAGE = "refactoring.movePackage";
  private static final String MOVE_CLASS   = "refactoring.moveClass";

  public static final String INTRODUCE_VARIABLE = "refactoring.introduceVariable";
  public static final String INTRODUCE_FIELD = "refactoring.introduceField";
  public static final String INTRODUCE_CONSTANT = "refactoring.introduceConstant";
  public static final String EXTRACT_METHOD     = "refactoring.extractMethod";

  public static final String ANONYMOUS_TO_INNER = "refactoring.convertAnonymous";
  public static final String LOCAL_TO_FIELD     = "refactoring.convertLocal";
  public static final String CHANGE_SIGNATURE   = "refactoring.changeSignature";
  public static final String ENCAPSULATE_FIELDS = "refactoring.encapsulateFields";
  public static final String EXTRACT_INTERFACE  = "refactoring.extractInterface";
  public static final String EXTRACT_SUPERCLASS = "refactoring.extractSuperclass";
  public static final String MOVE_INNER_UPPER   = "refactoring.moveInner";
  public static final String REPLACE_TEMP_WITH_QUERY = "refactoring.replaceTemp";
  public static final String MOVE_MEMBERS       = "refactoring.moveMembers";
  public static final String INLINE_CLASS       = "refactoring.inlineClass";
  public static final String INLINE_METHOD      = "refactoring.inlineMethod";
  public static final String INLINE_CONSTRUCTOR = "refactoring.inlineConstructor";
  public static final String INLINE_VARIABLE    = "refactoring.inlineVariable";
  public static final String INLINE_FIELD       = "refactoring.inlineField";

  public static final String MIGRATION          = "refactoring.migrate";

  public static final String COPY_CLASS         = "refactoring.copyClass";

  public static final String MAKE_METHOD_STATIC       = "refactoring.makeMethodStatic";
  public static final String MAKE_METHOD_STATIC_SIMPLE  = "refactoring.makeMethodStatic";

  public static final String INTRODUCE_PARAMETER        = "refactoring.introduceParameter";
  public static final String TURN_REFS_TO_SUPER         = "refactoring.useInterface";
  public static final String MEMBERS_PULL_UP            = "refactoring.pullMembersUp";
  public static final String MEMBERS_PUSH_DOWN          = "refactoring.pushMembersDown";
  public static final String INHERITANCE_TO_DELEGATION        = "refactoring.replaceInheritWithDelegat";
  public static final String REPLACE_CONSTRUCTOR_WITH_FACTORY = "refactoring.replaceConstrWithFactory";
  public static final String SAFE_DELETE                      = "refactoring.safeDelete";
  public static final String SAFE_DELETE_OVERRIDING           = "refactoring.safeDelete.overridingMethods";
  public static final String EJB_RENAME                 = "refactoring.rename.ejbRename";
  public static final String TYPE_COOK                  = "refactoring.generify";
  public static final String CONVERT_TO_INSTANCE_METHOD = "refactoring.convertToInstanceMethod";
  public static final String METHOD_DUPLICATES          = "refactoring.replaceMethodCodeDuplicates";
  public static final String CHANGE_CLASS_SIGNATURE     = "change.class.signature.dialog";
  public static final String MOVE_INSTANCE_METHOD       = "refactoring.moveInstMethod";
  public static final String EXTRACT_METHOD_OBJECT = "refactoring.extractMethodObject";
  public static final String REPLACE_CONSTRUCTOR_WITH_BUILDER = "refactoring.replaceConstructorWithBuilder";
  @NonNls public static final String ExtractClass = "refactorj.extractClass";
  @NonNls public static final String IntroduceParameterObject = "refactorj.introduceParameterObject";
  @NonNls public static final String RemoveMiddleman = "refactorj.removeMiddleman";
  @NonNls public static final String WrapReturnValue = "refactorj.wrapReturnValue";

  public static String getMoveHelpID(PsiElement element) {
    if (element instanceof PsiPackage){
      return MOVE_PACKAGE;
    }
    else if (element instanceof PsiClass){
      return MOVE_CLASS;
    }
    else{
      return null;
    }
  }
}
