package com.intellij.compiler.make;

import com.intellij.compiler.classParsing.FieldInfo;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 7, 2004
 */
class FieldChangeDescription extends ChangeDescription {
  public final boolean flagsChanged;
  public final boolean descriptorChanged;
  public final boolean genericSignatureChanged;

  public FieldChangeDescription(final Cache oldCache, final Cache newCache, final int oldFieldId, final int newFieldId) throws CacheCorruptedException {
    descriptorChanged = oldCache.getFieldDescriptor(oldFieldId) != newCache.getFieldDescriptor(newFieldId);

    flagsChanged = oldCache.getFieldFlags(oldFieldId) != newCache.getFieldFlags(newFieldId);

    final int oldGenericSignature = oldCache.getFieldGenericSignature(oldFieldId);
    final int newGenericSignature = newCache.getFieldGenericSignature(newFieldId);
    genericSignatureChanged = oldGenericSignature != newGenericSignature;
  }

  public FieldChangeDescription(final FieldInfo oldField, final FieldInfo newField) {
    descriptorChanged = oldField.getDescriptor() != newField.getDescriptor();
    flagsChanged = oldField.getFlags() != newField.getFlags();
    genericSignatureChanged = oldField.getGenericSignature() != newField.getGenericSignature();
  }

  public boolean isChanged() {
    return flagsChanged || descriptorChanged || genericSignatureChanged;
  }
}
