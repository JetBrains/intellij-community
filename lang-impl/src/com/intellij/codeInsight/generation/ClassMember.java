/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

/**
 * @author peter
 */
public interface ClassMember extends MemberChooserObject {
  ClassMember[] EMPTY_ARRAY = new ClassMember[0];

  /**
   * @return should override equals() and hashCode()
   */
  MemberChooserObject getParentNodeDelegate();

}
