class C {
    void foo() {
        for(int i = 0; i < 10; i++){
        if (i < 10){ continue;}
         System.out.println("");
        }
    }//ins and outs
//in: PsiLocalVariable:i
//out: OUTSIDE null

    {
        for(int i = 0; i < 10; i++){
          if (i < 10){ continue;}
        }
    }
}