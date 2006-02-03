/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.reference;

/**
 * A node in the reference graph corresponding to a project. A single instance of this
 * node exists in the graph.
 *
 * @see RefManager#getRefProject() 
 */
public interface RefProject extends RefEntity {
  RefPackage getDefaultPackage();
}
