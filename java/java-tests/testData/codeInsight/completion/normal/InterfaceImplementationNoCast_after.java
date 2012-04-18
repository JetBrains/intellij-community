public class Util2 {
  void foo(XmlAttribute o ) {
    if (o instanceof XmlAttributeImpl) {
      o.getParent()<caret>
    }
  }
}

interface PsiElement {
  PsiElement getParent();
}
interface XmlAttribute extends PsiElement {}
class XmlAttributeImpl implements XmlAttribute {
  @Override
  public PsiElement getParent() {
    return null;
  }
}