// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.BitUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Table of presently used flag bits, a minus (-) signifies the bit is free.
 * <table>
 *   <thead> <tr><th>Bits<th>Usage<th>User(s)</thead>
 *   <tbody>
 *      <tr valign="top"><td>1<td><pre>access modifiers</pre><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}
 *
 *      <tr valign="top"><td>2<td><pre>access modifiers</pre><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}
 *
 *      <tr valign="top"><td>3<td><pre>is static</pre><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}
 *
 *      <tr valign="top"><td>4<td><pre>is final</pre><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}
 *
 *      <tr valign="top"><td>5<td><pre>is deleted</pre><td>{@link RefElementImpl}
 *
 *      <tr valign="top"><td>6<td><pre>is initialized</pre><td>{@link RefElementImpl}
 *
 *      <tr valign="top"><td>7<td><pre>is reachable</pre><td>{@link RefElementImpl}
 *
 *      <tr valign="top"><td>8<td><pre>is entry point</pre><td>{@link RefElementImpl}
 *
 *      <tr valign="top"><td>9<td><pre>is permanent entry</pre><td>{@link RefElementImpl}
 *
 *      <tr valign="top"><td>10<td><pre>references built</pre><td>{@link RefElementImpl}
 *
 *      <tr valign="top"><td>11<td><pre>is synthetic jsp element</pre><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}
 *
 *      <tr valign="top"><td><td><pre>is from common</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefElementImpl}
 *
 *      <tr valign="top"><td>12<td><pre>is forbid protected access</pre><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}
 *
 *      <tr valign="top"><td><td><pre>is traversed</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefElementImpl}
 *
 *      <tr valign="top"><td>13<td>-<td>-
 *
 *      <tr valign="top"><td>14<td>-<td>-
 *
 *      <tr valign="top"><td>15<td>-<td>-
 *
 *      <tr valign="top"><td>16<td>-<td>-
 *
 *      <tr valign="top"><td>17<td><pre>is anonymous</pre><td>{@link com.intellij.codeInspection.reference.RefClassImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>used for reading</pre><td>{@link com.intellij.codeInspection.reference.RefFieldImpl},
 *      {@link com.intellij.codeInspection.reference.RefParameterImpl},
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFieldImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefParameterImpl}
 *      <tr valign="top"><td><td><pre>is appmain</pre><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}
 *      <tr valign="top"><td><td><pre>has body</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFunctionImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}
 *
 *      <tr valign="top"><td>18<td><pre>is interface</pre><td>{@link com.intellij.codeInspection.reference.RefClassImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>used for writing</pre><td>{@link com.intellij.codeInspection.reference.RefFieldImpl},
 *      {@link com.intellij.codeInspection.reference.RefParameterImpl},
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFieldImpl},
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefParameterImpl}
 *      <tr valign="top"><td><td><pre>is library override</pre><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}
 *      <tr valign="top"><td><td><pre>empty body</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFunctionImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}
 *
 *      <tr valign="top"><td>19<td><pre>is utility</pre><td>{@link com.intellij.codeInspection.reference.RefClassImpl}
 *      <tr valign="top"><td><td><pre>assigned only in initializer</pre><td>{@link com.intellij.codeInspection.reference.RefFieldImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFieldImpl}
 *      <tr valign="top"><td><td><pre>is constructor</pre><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}
 *      <tr valign="top"><td><td><pre>closure</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFunctionImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}
 *
 *      <tr valign="top"><td>20<td><pre>is abstract</pre><td>{@link com.intellij.codeInspection.reference.RefClassImpl},
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl} &
 *      {@link com.intellij.codeInspection.reference.RefMethodImpl}
 *      <tr valign="top"><td><td><pre>implicitly used from core</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFunctionImpl}
 *      <tr valign="top"><td><td><pre>constructor</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}
 *      <tr valign="top"><td><td><pre>implicitly read</pre><td>{@link com.intellij.codeInspection.reference.RefFieldImpl}
 *
 *      <tr valign="top"><td>21<td><pre>is body empty</pre><td>{@link RefMethodImpl} & {@link RefFunctionalExpressionImpl}
 *      <tr valign="top"><td><td><pre>is final</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>with static constructor</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}
 *      <tr valign="top"><td><td><pre>implicitly written</pre><td>{@link com.intellij.codeInspection.reference.RefFieldImpl}
 *
 *      <tr valign="top"><td>22<td><pre>is applet</pre><td>{@link com.intellij.codeInspection.reference.RefClassImpl}
 *      <tr valign="top"><td><td><pre>only calls super</pre><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}
 *      <tr valign="top"><td><td><pre>is trait</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>is enum constant</pre><td>{@link com.intellij.codeInspection.reference.RefFieldImpl}
 *
 *      <tr valign="top"><td>23<td><pre>is servlet</pre><td>{@link com.intellij.codeInspection.reference.RefClassImpl}
 *      <tr valign="top"><td><td><pre>is return value used</pre><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}
 *      <tr valign="top"><td><td><pre>is with implicit constructor</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>access private</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}
 *
 *      <tr valign="top"><td>24<td><pre>is test case</pre><td>{@link com.intellij.codeInspection.reference.RefClassImpl}
 *      <tr valign="top"><td><td><pre>is utility</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>access protected</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}
 *
 *      <tr valign="top"><td>25<td><pre>is local</pre><td>{@link com.intellij.codeInspection.reference.RefClassImpl}
 *      <tr valign="top"><td><td><pre>is test</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>access public</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}
 *
 *      <tr valign="top"><td>26<td><pre>is implicitly used constructor</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>is enum class</pre><td>{@link RefClassImpl}
 *
 *      <tr valign="top"><td>27<td><pre>is with duplicates</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>is abstract</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}
 *
 *      <tr valign="top"><td>28<td><pre>is called on subclass</pre><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}
 *      <tr valign="top"><td><td><pre>is duplicates initiator</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}
 *      <tr valign="top"><td><td><pre>is dynamic</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}
 *
 *      <tr valign="top"><td>29<td><pre>is static</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}
 *
 *      <tr valign="top"><td>30<td><pre>is final</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}
 *
 *      <tr valign="top"><td>31<td><pre>was used by static reference</pre><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}
 *   </tbody>
 * </table>
 * <br>
 * Dynamic masks:
 * <ul>
 * <li>can be final mask {@link com.intellij.codeInspection.canBeFinal.CanBeFinalAnnotator} &
 * {@link com.intellij.codeInspection.canBeFinal.CanBeFinalInspection}
 * <li>is EJB declaration {@link com.intellij.javaee.ejb.extensions.EjbGraphAnnotator}
 * <li>is EJB implementation {@link com.intellij.javaee.ejb.extensions.EjbGraphAnnotator}
 * <li>is EJB {@link com.intellij.javaee.ejb.extensions.EjbGraphAnnotator}
 * </ul>
 */
public abstract class RefEntityImpl extends UserDataHolderBase implements RefEntity, WritableRefEntity {
  private WritableRefEntity myOwner; // guarded by this
  private List<RefEntity> myChildren; // guarded by this
  private final String myName;
  protected long myFlags; // guarded by this
  protected final RefManagerImpl myManager;

  protected RefEntityImpl(@NotNull String name, @NotNull RefManager manager) {
    myManager = (RefManagerImpl)manager;
    myName = myManager.internName(name);
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getQualifiedName() {
    return myName;
  }

  @Override
  public synchronized @NotNull List<RefEntity> getChildren() {
    return ObjectUtils.notNull(myChildren, ContainerUtil.emptyList());
  }

  @Override
  public synchronized WritableRefEntity getOwner() {
    return myOwner;
  }

  @Override
  public synchronized void setOwner(final @Nullable WritableRefEntity owner) {
    myOwner = owner;
  }

  @Override
  public synchronized void add(final @NotNull RefEntity child) {
    addChild(child);
    ((RefEntityImpl)child).setOwner(this);
  }

  protected synchronized void addChild(@NotNull RefEntity child) {
    List<RefEntity> children = myChildren;
    if (children == null) {
      myChildren = children = new ArrayList<>(1);
    }
    children.add(child);
  }

  @Override
  public synchronized void removeChild(final @NotNull RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
    }
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public void accept(final @NotNull RefVisitor refVisitor) {
    DumbService.getInstance(myManager.getProject()).runReadActionInSmartMode(() -> refVisitor.visitElement(this));
  }

  public synchronized boolean checkFlag(long mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  public synchronized void setFlag(final boolean value, final long mask) {
    myFlags = BitUtil.set(myFlags, mask, value);
  }

  @Override
  public String getExternalName() {
    return myName;
  }

  @Override
  public @NotNull RefManagerImpl getRefManager() {
    return myManager;
  }
}
