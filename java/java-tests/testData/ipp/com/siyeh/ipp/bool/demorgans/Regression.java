class Regression {

  void x(String element) {
    boolean b =  element instanceof Stri<caret>ng && "i18n.py".equals(((String)element).substring(1));
  }
}