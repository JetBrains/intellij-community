/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide;

/**
 * @author mike
 */
public interface XmlRpcServer {
 void addHandler (String name, Object handler);
 void removeHandler (String name);
 public int getPortNumber();
}
