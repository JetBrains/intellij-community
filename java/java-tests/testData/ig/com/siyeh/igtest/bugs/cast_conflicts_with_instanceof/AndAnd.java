package com.siyeh.igtest.bugs.castConflictingInstanceof.andAnd;

public class AndAnd {

    public String getCanonicalText(PsiElement resolved) {
        if (resolved instanceof PsiMember || resolved instanceof PsiNamedElement) {
            PsiClass clazz = ((PsiMember) resolved).getContainingClass();
            if (clazz != null) {
                String qName = clazz.getQualifiedName();
                if (qName != null) {
                    return qName + "." + ((PsiNamedElement) resolved).getName();
                }
            }
        }

        return null;
    }

    interface PsiElement {}
    interface PsiMember {

        PsiClass getContainingClass();
    }
    interface PsiNamedElement {
        String getName();
    }
    interface PsiClass extends PsiElement {
        String getQualifiedName();
    }
}
