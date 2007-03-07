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

  private final boolean myIsPersistent;
  private RefElement myRefElement;
  private String myFQName;
  private String myType;
    @NonNls
    public static final String FQNAME_ATTR = "FQNAME";
    @NonNls
    public static final String TYPE_ATTR = "TYPE";
  @NonNls public static final String ENTRY_POINT = "entry_point";

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

    initType(type);
  }

  public SmartRefElementPointerImpl(final String type, final String fqName) {
     myIsPersistent = true;
     myFQName = fqName;
     myType = type;
     initType(type);
   }

  private void initType(final String type) {
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

  public SmartRefElementPointerImpl(final String type, final String fqName, final RefManager manager) {
    myIsPersistent = false;
    myFQName = fqName;
    initType(type);
    resolve(manager);
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

  public void writeExternal(Element parentNode) {
    Element element = new Element(ENTRY_POINT);
    element.setAttribute(TYPE_ATTR, myType);
    element.setAttribute(FQNAME_ATTR, getFQName());
    if (myRefElement != null) {
      final RefEntity entity = myRefElement.getOwner();
      if (entity instanceof RefElement) {
        new SmartRefElementPointerImpl((RefElement)entity, myIsPersistent).writeExternal(element);        
      }
    }
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
