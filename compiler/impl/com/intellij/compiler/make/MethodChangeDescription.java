package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.ConstantValue;
import com.intellij.compiler.classParsing.GenericMethodSignature;
import com.intellij.compiler.classParsing.SignatureParsingException;
import com.intellij.util.cls.ClsUtil;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 7, 2004
 */
class MethodChangeDescription extends ChangeDescription {
  public final boolean returnTypeDescriptorChanged;
  public final boolean returnTypeGenericSignatureChanged;
  public final boolean throwsListChanged;
  public final boolean flagsChanged;
  public final boolean staticPropertyChanged;
  public final boolean accessRestricted;
  public final boolean becameAbstract;
  public final boolean removedAnnotationDefault;

  // TODO: handle changes of parameters?
  public MethodChangeDescription(final Cache oldCache, final Cache newCache, final int oldMethod, final int newMethod, SymbolTable symbolTable) throws CacheCorruptedException {
    final String oldRtDescriptor = CacheUtils.getMethodReturnTypeDescriptor(oldCache, oldMethod, symbolTable);
    final String newRtDescriptor = CacheUtils.getMethodReturnTypeDescriptor(newCache, newMethod, symbolTable);
    returnTypeDescriptorChanged = !oldRtDescriptor.equals(newRtDescriptor);

    final int oldGenericSignature = oldCache.getMethodGenericSignature(oldMethod);
    final int newGenericSignature = newCache.getMethodGenericSignature(newMethod);
    if (oldGenericSignature == newGenericSignature) {
      returnTypeGenericSignatureChanged = false;
    }
    else {
      if (oldGenericSignature != -1 && newGenericSignature != -1) {
        try {
          final GenericMethodSignature _oldGenericMethodSignature = GenericMethodSignature.parse(symbolTable.getSymbol(oldGenericSignature));
          final GenericMethodSignature _newGenericMethodSignature = GenericMethodSignature.parse(symbolTable.getSymbol(newGenericSignature));
          returnTypeGenericSignatureChanged = !_oldGenericMethodSignature.getReturnTypeSignature().equals(_newGenericMethodSignature.getReturnTypeSignature());
        }
        catch (SignatureParsingException e) {
          throw new CacheCorruptedException(e);
        }
      }
      else {
        returnTypeGenericSignatureChanged = true;
      }
    }

    throwsListChanged = !CacheUtils.areArraysContentsEqual(oldCache.getMethodThrownExceptions(oldMethod), newCache.getMethodThrownExceptions(newMethod));

    final int oldFlags = oldCache.getMethodFlags(oldMethod);
    final int newFlags = newCache.getMethodFlags(newMethod);
    flagsChanged = oldFlags != newFlags;

    staticPropertyChanged = (ClsUtil.isStatic(oldFlags) && !ClsUtil.isStatic(newFlags)) ||  (!ClsUtil.isStatic(oldFlags) && ClsUtil.isStatic(newFlags)); // was not static and became static or was static and became not static
    accessRestricted = MakeUtil.isMoreAccessible(oldFlags, newFlags);
    becameAbstract = !ClsUtil.isAbstract(oldFlags) && ClsUtil.isAbstract(newFlags);

    final ConstantValue oldDefault = oldCache.getAnnotationDefault(oldMethod);
    final ConstantValue newDefault = newCache.getAnnotationDefault(newMethod);
    removedAnnotationDefault = (oldDefault != null && !ConstantValue.EMPTY_CONSTANT_VALUE.equals(oldDefault)) && (newDefault == null || ConstantValue.EMPTY_CONSTANT_VALUE.equals(newDefault));
  }

  public boolean isChanged() {
    return returnTypeDescriptorChanged || throwsListChanged || flagsChanged || returnTypeGenericSignatureChanged || removedAnnotationDefault;
  }
}
