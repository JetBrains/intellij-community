/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.meta;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.05.2003
 * Time: 2:33:05
 * To change this template use Options | File Templates.
 */
public interface PsiMetaOwner{
  PsiMetaData getMetaData();
  boolean isMetaEnough();
}
