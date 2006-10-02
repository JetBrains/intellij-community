/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2001
 * Time: 2:40:54 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class SmartRefElementPointerImpl implements SmartRefElementPointer {
  @NonNls private static final String CLASS = "class";
  @NonNls private static final String METHOD = "method";
  @NonNls private static final String FIELD = "field";

  @NonNls private static final String FILE = "file";
  @NonNls private static final String PARAMETER = "parameter";

  private final boolean myIsPersistent;
  private RefElement myRefElement;
  private String myFQName;
  private final String myType;
    @NonNls
    private static final String FQNAME_ATTR = "FQNAME";
    @NonNls
    private static final String TYPE_ATTR = "TYPE";

    public SmartRefElementPointerImpl(RefElement ref, boolean isPersistent) {
      myIsPersistent = isPersistent;
      myRefElement = ref;
      if (ref instanceof RefImplicitConstructor) {
        ref = ((RefImplicitConstructor)ref).getOwnerClass();
      }

      myFQName = ref.getExternalName();

      if (ref instanceof RefMethod) {
        myType = METHOD;
      } else if (ref instanceof RefClass) {
        myType = CLASS;
      } else if (ref instanceof RefField) {
        myType = FIELD;
      } else if (ref instanceof RefFile) {
        myType = FILE;
      } else if (ref instanceof RefParameter) {
        myType = PARAMETER;
      } else {
        myType = null;
      }
    }

  public SmartRefElementPointerImpl(Element jDomElement) {
    myIsPersistent = true;
    myRefElement = null;
    myFQName = jDomElement.getAttributeValue(FQNAME_ATTR);
    String type = jDomElement.getAttributeValue(TYPE_ATTR);

    if (METHOD.equals(type)) {
      myType = METHOD;
    } else if (CLASS.equals(type)) {
      myType = CLASS;
    } else if (FIELD.equals(type)) {
      myType = FIELD;
    } else if (FILE.equals(type)) {
      myType = FILE;
    } else if (PARAMETER.equals(type)) {
      myType = PARAMETER;
    } else {
      myType = null;
    }
  }

  public boolean isPersistent() {
    return myIsPersistent;
  }

  public String getFQName() {
    return myFQName;
  }

  public RefElement getRefElement() {
    return myRefElement;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element parentNode) {
    Element element = new Element("entry_point");
    element.setAttribute(TYPE_ATTR, myType);
    element.setAttribute(FQNAME_ATTR, getFQName());
    parentNode.addContent(element);
  }

  public boolean resolve(RefManager manager) {
    if (myRefElement != null) {
      if (myRefElement.isValid()) return true;
      return false;
    }

    if (METHOD.equals(myType)) {
      myRefElement = RefMethodImpl.methodFromExternalName(manager, getFQName());
    } else if (CLASS.equals(myType)) {
      RefClass refClass= RefClassImpl.classFromExternalName(manager, getFQName());
      if (refClass != null) {
        myRefElement = refClass.getDefaultConstructor();
        if (myRefElement == null) myRefElement = refClass;
      }
    } else if (FIELD.equals(myType)) {
      myRefElement = RefFieldImpl.fieldFromExternalName(manager, getFQName());
    } else if (FILE.equals(myType)) {
      myRefElement = RefFileImpl.fileFromExternalName(manager, getFQName());
    } else if (PARAMETER.equals(myType)) {
      myRefElement = RefParameterImpl.parameterFromExternalName(manager, getFQName());
    }

    return myRefElement != null;
  }

  public void freeReference() {
    myRefElement = null;
  }
}
