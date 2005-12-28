/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.reference;

/**
 * User: anna
 * Date: 28-Dec-2005
 */
public interface RefProject extends RefEntity {
  RefPackage getDefaultPackage();
}
