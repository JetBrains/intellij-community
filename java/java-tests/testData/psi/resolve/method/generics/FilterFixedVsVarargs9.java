 class ParentOf {
     public static <T extends PsiElement> T getParentOfType(PsiElement element, Class<T> aClass, boolean strict) {
       return null;
     }
     public static <T extends PsiElement> T getParentOfType(PsiElement element, Class<? extends T>... classes) {
       return null;
     }

     void f(PsiElement e) {
         <ref>getParentOfType(e, PsiElement.class, PsiElement.class);
     }

     static class PsiElement {}
 }
