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
package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.ConstantValue;
import com.intellij.compiler.classParsing.GenericMethodSignature;
import com.intellij.compiler.classParsing.MethodInfo;
import com.intellij.compiler.classParsing.SignatureParsingException;
import com.intellij.util.cls.ClsUtil;

import java.util.Arrays;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 7, 2004
 */
class MethodChangeDescription extends ChangeDescription {
  public final boolean returnTypeDescriptorChanged;
  public final boolean returnTypeGenericSignatureChanged;
  public final boolean paramsGenericSignatureChanged;
  public final boolean throwsListChanged;
  public final boolean flagsChanged;
  public final boolean staticPropertyChanged;
  public final boolean accessRestricted;
  public final boolean becameAbstract;
  public final boolean removedAnnotationDefault;

  public MethodChangeDescription(final MethodInfo oldMethod, final MethodInfo newMethod, SymbolTable symbolTable) throws CacheCorruptedException {
    final String oldRtDescriptor = oldMethod.getReturnTypeDescriptor(symbolTable);
    final String newRtDescriptor = newMethod.getReturnTypeDescriptor(symbolTable);
    returnTypeDescriptorChanged = !oldRtDescriptor.equals(newRtDescriptor);

    final int oldGenericSignature = oldMethod.getGenericSignature();
    final int newGenericSignature = newMethod.getGenericSignature();
    if (oldGenericSignature == newGenericSignature) {
      returnTypeGenericSignatureChanged = false;
      paramsGenericSignatureChanged = false;
    }
    else {
      if (oldGenericSignature != -1 && newGenericSignature != -1) {
        try {
          final GenericMethodSignature _oldGenericMethodSignature = GenericMethodSignature.parse(symbolTable.getSymbol(oldGenericSignature));
          final GenericMethodSignature _newGenericMethodSignature = GenericMethodSignature.parse(symbolTable.getSymbol(newGenericSignature));
          returnTypeGenericSignatureChanged = !_oldGenericMethodSignature.getReturnTypeSignature().equals(_newGenericMethodSignature.getReturnTypeSignature());
          paramsGenericSignatureChanged = !_oldGenericMethodSignature.getFormalTypeParams().equals(_newGenericMethodSignature.getFormalTypeParams()) ||
                                          !Arrays.equals(_oldGenericMethodSignature.getParamSignatures(), _newGenericMethodSignature.getParamSignatures());
        }
        catch (SignatureParsingException e) {
          throw new CacheCorruptedException(e);
        }
      }
      else {
        returnTypeGenericSignatureChanged = true;
        paramsGenericSignatureChanged = true;
      }
    }

    throwsListChanged = !CacheUtils.areArraysContentsEqual(oldMethod.getThrownExceptions(), newMethod.getThrownExceptions());

    final int oldFlags = oldMethod.getFlags();
    final int newFlags = newMethod.getFlags();
    flagsChanged = oldFlags != newFlags;

    staticPropertyChanged = (ClsUtil.isStatic(oldFlags) && !ClsUtil.isStatic(newFlags)) ||  (!ClsUtil.isStatic(oldFlags) && ClsUtil.isStatic(newFlags)); // was not static and became static or was static and became not static
    accessRestricted = MakeUtil.isMoreAccessible(oldFlags, newFlags);
    becameAbstract = !ClsUtil.isAbstract(oldFlags) && ClsUtil.isAbstract(newFlags);

    final ConstantValue oldDefault = oldMethod.getAnnotationDefault();
    final ConstantValue newDefault = newMethod.getAnnotationDefault();
    removedAnnotationDefault = (oldDefault != null && !ConstantValue.EMPTY_CONSTANT_VALUE.equals(oldDefault)) && (newDefault == null || ConstantValue.EMPTY_CONSTANT_VALUE.equals(newDefault));
  }

  public boolean isChanged() {
    return returnTypeDescriptorChanged || throwsListChanged || flagsChanged || returnTypeGenericSignatureChanged || paramsGenericSignatureChanged || removedAnnotationDefault;
  }
}
