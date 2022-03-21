// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
 *   <thead>
 *     <tr><th>Bits</th><th>Usage</th><th>User(s)</th></tr>
 *   </thead>
 *   <tbody>
 *      <tr valign="top"><td>1</td><td><pre>access modifiers</pre></td><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}</td></tr>
 *      <tr valign="top"><td>2</td><td><pre>access modifiers</pre></td><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}</td></tr>
 *      <tr valign="top"><td>3</td><td><pre>is static</pre></td><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}</td></tr>
 *      <tr valign="top"><td>4</td><td><pre>is final</pre></td><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}</td></tr>
 *      <tr valign="top"><td>5</td><td><pre>is deleted</pre></td><td>{@link RefElementImpl}</td></tr>
 *      <tr valign="top"><td>6</td><td><pre>is initialized</pre></td><td>{@link RefElementImpl}</td></tr>
 *      <tr valign="top"><td>7</td><td><pre>is reachable</pre></td><td>{@link RefElementImpl}</td></tr>
 *      <tr valign="top"><td>8</td><td><pre>is entry point</pre></td><td>{@link RefElementImpl}</td></tr>
 *      <tr valign="top"><td>9</td><td><pre>is permanent entry</pre></td><td>{@link RefElementImpl}</td></tr>
 *      <tr valign="top"><td>10</td><td>-</td><td>-</td></tr>
 *      <tr valign="top"><td>11</td><td><pre>is synthetic jsp element</pre></td><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is from common</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefElementImpl}</td></tr>
 *      <tr valign="top"><td>12</td><td><pre>is forbid protected access</pre></td><td>{@link com.intellij.codeInspection.reference.RefJavaElementImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is traversed</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefElementImpl}</td></tr>
 *      <tr valign="top"><td>13</td><td>-</td><td>-</td></tr>
 *      <tr valign="top"><td>14</td><td>-</td><td>-</td></tr>
 *      <tr valign="top"><td>15</td><td>-</td><td>-</td></tr>
 *      <tr valign="top"><td>16</td><td>-</td><td>-</td></tr>
 *      <tr valign="top"><td>17</td><td><pre>is anonymous</pre></td><td>{@link com.intellij.codeInspection.reference.RefClassImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>used for reading</pre></td><td>{@link com.intellij.codeInspection.reference.RefFieldImpl},
 *      {@link com.intellij.codeInspection.reference.RefParameterImpl},
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFieldImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefParameterImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is appmain</pre></td><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}<br>
 *      <tr valign="top"><td></td><td><pre>has body</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFunctionImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}</td></tr>
 *      <tr valign="top"><td>18</td><td><pre>is interface</pre></td><td>{@link com.intellij.codeInspection.reference.RefClassImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>used for writing</pre></td><td>{@link com.intellij.codeInspection.reference.RefFieldImpl},
 *      {@link com.intellij.codeInspection.reference.RefParameterImpl},
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFieldImpl},
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefParameterImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is library override</pre></td><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}<br>
 *      <tr valign="top"><td></td><td><pre>empty body</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFunctionImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}</td></tr>
 *      <tr valign="top"><td>19</td><td><pre>is utility</pre></td><td>{@link com.intellij.codeInspection.reference.RefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>assigned only in initializer</pre></td><td>{@link com.intellij.codeInspection.reference.RefFieldImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFieldImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is constructor</pre></td><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}<br>
 *      <tr valign="top"><td></td><td><pre>closure</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFunctionImpl} &
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}<br></td></tr>
 *      <tr valign="top"><td>20</td><td><pre>is abstract</pre></td><td>{@link com.intellij.codeInspection.reference.RefClassImpl},
 *      {@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl} &
 *      {@link com.intellij.codeInspection.reference.RefMethodImpl}<br>
 *      <tr valign="top"><td></td><td><pre>implicitly used from core</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefFunctionImpl}<br>
 *      <tr valign="top"><td></td><td><pre>constructor</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}</td></tr>
 *      <tr valign="top"><td>21</td><td><pre>is body empty</pre></td><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is final</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>with static constructor</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefMethodImpl}</td></tr>
 *      <tr valign="top"><td>22</td><td><pre>is applet</pre></td><td>{@link com.intellij.codeInspection.reference.RefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>only calls super</pre></td><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is trait</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}</td></tr>
 *      <tr valign="top"><td>23</td><td><pre>is servlet</pre></td><td>{@link com.intellij.codeInspection.reference.RefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is return value used</pre></td><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is with implicit constructor</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>access private</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}</td></tr>
 *      <tr valign="top"><td>24</td><td><pre>is test case</pre></td><td>{@link com.intellij.codeInspection.reference.RefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is utility</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>access protected</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}</td></tr>
 *      <tr valign="top"><td>25</td><td><pre>is local</pre></td><td>{@link com.intellij.codeInspection.reference.RefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is test</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>access public</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}</td></tr>
 *      <tr valign="top"><td>26</td><td><pre>is implicitly used constructor</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}</td></tr>
 *      <tr valign="top"><td>27</td><td><pre>-</pre></td><td>-<br>
 *      <tr valign="top"><td></td><td><pre>is with duplicates</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is abstract</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}</td></tr>
 *      <tr valign="top"><td>28</td><td><pre>is called on subclass</pre></td><td>{@link com.intellij.codeInspection.reference.RefMethodImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is duplicates initiator</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassImpl}<br>
 *      <tr valign="top"><td></td><td><pre>is dynamic</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}</td></tr>
 *      <tr valign="top"><td>29</td><td><pre>is static</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}</td></tr>
 *      <tr valign="top"><td>30</td><td><pre>is final</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}</td></tr>
 *      <tr valign="top"><td>31</td><td><pre>was used by static reference</pre></td><td>{@link com.jetbrains.php.lang.inspections.reference.elements.PhpRefClassMemberImpl}</td></tr>
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
  private volatile WritableRefEntity myOwner;
  private List<RefEntity> myChildren;  // guarded by this
  private final String myName;
  protected long myFlags; // guarded by this
  protected final RefManagerImpl myManager;

  protected RefEntityImpl(@NotNull String name, @NotNull RefManager manager) {
    myManager = (RefManagerImpl)manager;
    myName = myManager.internName(name);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return myName;
  }

  @NotNull
  @Override
  public synchronized List<RefEntity> getChildren() {
    return ObjectUtils.notNull(myChildren, ContainerUtil.emptyList());
  }

  @Override
  public WritableRefEntity getOwner() {
    return myOwner;
  }

  @Override
  public void setOwner(@Nullable final WritableRefEntity owner) {
    myOwner = owner;
  }

  @Override
  public synchronized void add(@NotNull final RefEntity child) {
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
  public synchronized void removeChild(@NotNull final RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
    }
  }

  public String toString() {
    return getName();
  }

  @Override
  public void accept(@NotNull final RefVisitor refVisitor) {
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

  @NotNull
  @Override
  public RefManagerImpl getRefManager() {
    return myManager;
  }
}
