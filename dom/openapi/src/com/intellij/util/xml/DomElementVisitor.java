/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 *  Visitor is a very common design pattern. DOM model also has a visitor, and it's called
 * DomElementVisitor. The {@link DomElement} interface has methods {@link DomElement#accept(DomElementVisitor)}
 * and {@link DomElement#acceptChildren(DomElementVisitor)}
 * that take this visitor as a parameter. DomElementVisitor has only one method:
 * {@link #visitDomElement(DomElement)}.
 * Where is the Visitor pattern? Where are all those methods with names like visitT(T) that
 * are usually found in it? There are no such methods, because the actual interfaces (T's)
 * arent known to anyone except you. But when you instantiate the DomElementVisitor
 * interface, you may add there those visitT() methods, and they will be called! You may
 * even name them just visit(), specify the type of the parameter, and everything will be
 * fine. For example, if you have two DOM element classes — Foo and Bar — your visitor
 * may look like this:
 *
 *  class MyVisitor implements DomElementVisitor {
 *    void visitDomElement(DomElement element) {}
 *    void visitFoo(Foo foo) {}
 *    void visitBar(Bar bar) {}
 *  }
 *
 * @author peter
 */
public interface DomElementVisitor {
  void visitDomElement(DomElement element);
}
