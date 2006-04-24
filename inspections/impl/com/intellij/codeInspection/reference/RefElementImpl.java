/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:28:53 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Stack;

public abstract class RefElementImpl extends RefEntityImpl implements RefElement {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.reference.RefElement");
  private static final int ACCESS_MODIFIER_MASK = 0x03;
  private static final int ACCESS_PRIVATE = 0x00;
  private static final int ACCESS_PROTECTED = 0x01;
  private static final int ACCESS_PACKAGE = 0x02;
  private static final int ACCESS_PUBLIC = 0x03;

  private static final int IS_STATIC_MASK = 0x04;
  private static final int IS_FINAL_MASK = 0x08;

  private static final int IS_REACHABLE_MASK = 0x40;
  private static final int IS_ENTRY_MASK = 0x80;
  private static final int IS_PERMANENT_ENTRY_MASK = 0x100;
  private static final int IS_USES_DEPRECATION_MASK = 0x200;
  private static final int IS_SYNTHETIC_JSP_ELEMENT = 0x400;


  private SmartPsiElementPointer myID;
  private final RefManager myManager;

  private ArrayList<RefElement> myOutReferences;
  private ArrayList<RefElement> myInReferences;

  private HashSet<RefClass> myOutTypeReferences;

  private boolean myIsDeleted ;
  private Module myModule;

  protected RefElementImpl(String name, RefElement owner) {
    super(name);
    myManager = owner.getRefManager();
    myID = null;
    myFlags = 0;
    myModule = ModuleUtil.findModuleForPsiElement(owner.getElement());

    String am = owner.getAccessModifier();
    final int access_id;

    if (PsiModifier.PRIVATE.equals(am)) {
      access_id = ACCESS_PRIVATE;
    }
    else if (PsiModifier.PUBLIC.equals(am)) {
      access_id = ACCESS_PUBLIC;
    }
    else if (PsiModifier.PACKAGE_LOCAL.equals(am)) {
      access_id = ACCESS_PACKAGE;
    }
    else {
      access_id = ACCESS_PROTECTED;
    }

    myFlags = ((myFlags >> 2) << 2) | access_id;

    final boolean synthOwner = owner.isSyntheticJSP();
    if (synthOwner) {
      setSyntheticJSP(true);
    }
  }

  protected RefElementImpl(PsiFile file, RefManager manager) {
    super(file.getName());
    myManager = manager;
    myID = SmartPointerManager.getInstance(manager.getProject()).createSmartPsiElementPointer(file);
    myFlags = 0;
    myModule = ModuleUtil.findModuleForPsiElement(file);
  }

  protected RefElementImpl(PsiModifierListOwner elem, RefManager manager) {
    super(getName(elem));
    myManager = manager;
    myID = SmartPointerManager.getInstance(manager.getProject()).createSmartPsiElementPointer(elem);
    myFlags = 0;
    myModule = ModuleUtil.findModuleForPsiElement(elem);

    setAccessModifier(RefUtil.getInstance().getAccessModifier(elem));

    //TODO[ik?] need more proper way to identify synthetic JSP holders
    final boolean isSynth = elem instanceof JspHolderMethod || elem instanceof JspClass;
    if (isSynth) {
      setSyntheticJSP(true);
    }

    initialize(elem);
  }

   public static String getName(PsiElement element) {
    if (element instanceof PsiAnonymousClass) {
      PsiAnonymousClass psiAnonymousClass = (PsiAnonymousClass)element;
      PsiClass psiBaseClass = psiAnonymousClass.getBaseClassType().resolve();
      return InspectionsBundle.message("inspection.reference.anonymous.name", (psiBaseClass != null ? psiBaseClass.getQualifiedName() : ""));
    }

    if (element instanceof JspClass) {
      final JspClass jspClass = (JspClass)element;
      final PsiFile jspxFile = jspClass.getContainingFile();
      return "<" + jspxFile.getName() + ">";
    }

    if (element instanceof JspHolderMethod) {
      return InspectionsBundle.message("inspection.reference.jsp.holder.method.anonymous.name");
    }

    String name = null;
    if (element instanceof PsiNamedElement) {
      name = ((PsiNamedElement)element).getName();
    }

    return name == null ? InspectionsBundle.message("inspection.reference.anonymous") : name;
  }

  protected void initialize(PsiModifierListOwner elem) {
    setIsStatic(elem.hasModifierProperty(PsiModifier.STATIC));
    setIsFinal(elem.hasModifierProperty(PsiModifier.FINAL));
  }

  public boolean isValid() {
    if (myIsDeleted) return false;
    final PsiElement element = getElement();
    return element != null && element.isPhysical();
  }

  public RefModule getModule() {
    return myManager.getRefModule(myModule);
  }

  public RefManager getRefManager() {
    return myManager;
  }

  public String getExternalName() {
    return getName();
  }

  public PsiElement getElement() {
    return myID.getElement();
  }

  public abstract void accept(RefVisitor visitor);

  public void buildReferences() {
  }

  protected void markReferenced(final RefElementImpl refFrom, PsiElement psiFrom, PsiElement psiWhat, final boolean forWriting, boolean forReading, PsiReferenceExpression expressionFrom) {
    addInReference(refFrom);
    ((RefManagerImpl)getRefManager()).fireNodeMarkedReferenced(this, refFrom, false);
  }

  public boolean isReachable() {
    return checkFlag(IS_REACHABLE_MASK);
  }

  public boolean isReferenced() {
    return getInReferences().size() > 0;
  }

  public boolean hasSuspiciousCallers() {
    for (RefElement refCaller : getInReferences()) {
      if (((RefElementImpl)refCaller).isSuspicious()) return true;
    }

    return false;
  }

  public boolean isSuspiciousRecursive() {
    return isCalledOnlyFrom(this, new Stack<RefElement>());
  }

  private boolean isCalledOnlyFrom(RefElement refElement, Stack<RefElement> callStack) {
    if (callStack.contains(this)) return refElement == this;
    if (getInReferences().size() == 0) return false;

    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refElement;
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        if (refSuper.getInReferences().size() > 0) return false;
      }
      if (refMethod.isConstructor()){
        boolean unreachable = true;
        for (RefElement refOut : refMethod.getOutReferences()){
          unreachable &= !refOut.isReachable();
        }
        if (unreachable) return true;
      }
    }

    callStack.push(this);
    for (RefElement refCaller : getInReferences()) {
      if (!((RefElementImpl)refCaller).isSuspicious() || !((RefElementImpl)refCaller).isCalledOnlyFrom(refElement, callStack)) {
        callStack.pop();
        return false;
      }
    }

    callStack.pop();
    return true;
  }

  public void addReference(RefElement refWhat, PsiElement psiWhat, PsiElement psiFrom, boolean forWriting, boolean forReading, PsiReferenceExpression expression) {
    if (refWhat != null) {
      addOutReference(refWhat);
      ((RefElementImpl)refWhat).markReferenced(this, psiFrom, psiWhat, forWriting, forReading, expression);
    }
  }

  public void addOutTypeRefernce(RefClass refClass){
    if (myOutTypeReferences == null){
      myOutTypeReferences = new HashSet<RefClass>();
    }
    myOutTypeReferences.add(refClass);
  }

  @NotNull
  public Collection<RefElement> getOutReferences() {
    if (myOutReferences == null){
      myOutReferences = new ArrayList<RefElement>();
    }
    return myOutReferences;
  }

  @NotNull
  public Collection<RefElement> getInReferences() {
    if (myInReferences == null){
      myInReferences = new ArrayList<RefElement>();
    }
    return myInReferences;
  }

  @NotNull
  public Collection<RefClass> getOutTypeReferences() {
    if (myOutTypeReferences == null){
      myOutTypeReferences = new HashSet<RefClass>();
    }
    return myOutTypeReferences;
  }

  public void addInReference(RefElement refElement) {
    if (!getInReferences().contains(refElement)) {
      getInReferences().add(refElement);
    }
  }

  public void addOutReference(RefElement refElement) {
    if (!getOutReferences().contains(refElement)) {
      getOutReferences().add(refElement);
    }
  }

  public boolean isFinal() {
    return checkFlag(IS_FINAL_MASK);
  }

  public boolean isStatic() {
    return checkFlag(IS_STATIC_MASK);
  }

  public void setIsStatic(boolean isStatic) {
    setFlag(isStatic, IS_STATIC_MASK);
  }

  public boolean isUsesDeprecatedApi() {
    return checkFlag(IS_USES_DEPRECATION_MASK);
  }

  public void setUsesDeprecatedApi(boolean usesDeprecatedApi) {
    setFlag(usesDeprecatedApi, IS_USES_DEPRECATION_MASK);
  }

  public void setIsFinal(boolean isFinal) {
    setFlag(isFinal, IS_FINAL_MASK);
  }

  public void setEntry(boolean entry) {
    setFlag(entry, IS_ENTRY_MASK);
  }

  public boolean isEntry() {
    return checkFlag(IS_ENTRY_MASK);
  }

  public boolean isPermanentEntry() {
    return checkFlag(IS_PERMANENT_ENTRY_MASK);
  }

  public void setPermanentEntry(boolean permanentEntry) {
    setFlag(permanentEntry, IS_PERMANENT_ENTRY_MASK);
  }

  public void setReachable(boolean reachable) {
    setFlag(reachable, IS_REACHABLE_MASK);
  }

  public boolean isSyntheticJSP() {
    return checkFlag(IS_SYNTHETIC_JSP_ELEMENT);
  }

  public void setSyntheticJSP(boolean b) {
    setFlag(b, IS_SYNTHETIC_JSP_ELEMENT);
  }

  public boolean isSuspicious() {
    return !isReachable();
  }

  @Nullable
  public String getAccessModifier() {
    long access_id = myFlags & ACCESS_MODIFIER_MASK;
    if (access_id == ACCESS_PRIVATE) return PsiModifier.PRIVATE;
    if (access_id == ACCESS_PUBLIC) return PsiModifier.PUBLIC;
    if (access_id == ACCESS_PACKAGE) return PsiModifier.PACKAGE_LOCAL;
    return PsiModifier.PROTECTED;
  }

  public void setAccessModifier(String am) {
    final int access_id;

    if (PsiModifier.PRIVATE.equals(am)) {
      access_id = ACCESS_PRIVATE;
    }
    else if (PsiModifier.PUBLIC.equals(am)) {
      access_id = ACCESS_PUBLIC;
    }
    else if (PsiModifier.PACKAGE_LOCAL.equals(am)) {
      access_id = ACCESS_PACKAGE;
    }
    else {
      access_id = ACCESS_PROTECTED;
    }

    myFlags = ((myFlags >> 2) << 2) | access_id;
  }

  public void referenceRemoved() {
    myIsDeleted = true;
    if (getOwner() != null) {
      ((RefEntityImpl)getOwner()).removeChild(this);
    }

    for (RefElement refCallee : getOutReferences()) {
      refCallee.getInReferences().remove(this);
    }

    for (RefElement refCaller : getInReferences()) {
      refCaller.getOutReferences().remove(this);
    }
  }

  public URL getURL() {
    try {
      final PsiFile containingFile = getElement().getContainingFile();
      if (containingFile == null) return null;
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return null;
      return new URL(virtualFile.getUrl() + "#" + getElement().getTextRange().getStartOffset());
    } catch (MalformedURLException e) {
      LOG.error(e);
    }

    return null;
  }

  protected abstract void initialize();

}
