class C {
    void foo() {
        for(int i = 0; i < 10; i++){
        if (i < 10){ continue;}
        }
    }//ins and outs
//in: PsiLocalVariable:i
//exit: CONTINUE PsiForStatement<-PsiContinueStatement

    {
        for(int i = 0; i < 10; i++){
          if (i < 10){ continue;}
        }
    }
}