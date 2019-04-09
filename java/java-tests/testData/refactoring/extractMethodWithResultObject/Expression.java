class AnnotationArgConverter {
  public GrAnnotationMemberValue convert(PsiAnnotationMemberValue value) {
    final StringBuilder buffer = new StringBuilder();

    buffer.append("@A(");
     
    <selection>value.accept(new JavaElementVisitor() {
      @Override
      public void visitExpression(PsiExpression expression) {
        buffer.append(expression.getText());
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
        if (arrayInitializer == null) {
          super.visitNewExpression(expression);
        }
        else {
          buffer.append(")");
        }
      }

    })</selection>;

    buffer.append(")");

    try {
      return GroovyPsiElementFactory.getInstance(value.getProject()).createAnnotationFromText(buffer.toString());
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  static class Project {}
  static class PsiExpression { String getText() {return "";}}
  static class PsiNewExpression extends PsiExpression {
    public PsiArrayInitializerExpression getArrayInitializer() { return null;}
  }
  static class PsiArrayInitializerExpression extends PsiExpression {}
  static class GrAnnotationMemberValue {}
  static class PsiAnnotationMemberValue {
    Project getProject() {return null;}
    void accept(JavaElementVisitor visitor) {}
  }
  static class GroovyPsiElementFactory {
    public static JVMElementFactory getInstance(Project project) { return new JVMElementFactory();}
  }
  static class JVMElementFactory {
    public GrAnnotationMemberValue createAnnotationFromText(String s) throws IncorrectOperationException { return null;}
  }
  static class JavaElementVisitor {
    public void visitExpression(PsiExpression expression) {}
    public void visitNewExpression(PsiNewExpression expression) {}
  }
  static class IncorrectOperationException extends Exception {}
}
