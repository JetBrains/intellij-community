public class AnnotationArgConverter {
  public GrAnnotationMemberValue convert(PsiAnnotationMemberValue value) {
    final StringBuilder buffer = new StringBuilder();

    buffer.append("@A(");
     
    newMethod(value, buffer);

    buffer.append(")");

    try {
      return GroovyPsiElementFactory.getInstance(value.getProject()).createAnnotationFromText(buffer.toString());
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

    private void newMethod(PsiAnnotationMemberValue value, final StringBuilder buffer) {
        value.accept(new JavaElementVisitor() {
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
    
        });
    }
}
