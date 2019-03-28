class C {
    void foo() {
        for(int i = 0; i < 10; i++){
        if (i < 10){ continue;}
         System.out.println("");
        }
    }//ins and outs
//in: PsiLocalVariable:i
//exit: CONTINUE PsiBlockStatement<-PsiContinueStatement
//exit: SEQUENTIAL PsiIfStatement

    public NewMethodResult newMethod(int i) {
        if (i < 10){
            return new NewMethodResult((1 /* exit key */));
        }
        return new NewMethodResult((-1 /* exit key */));
    }

    public class NewMethodResult {
        private int exitKey;

        public NewMethodResult(int exitKey) {
            this.exitKey = exitKey;
        }
    }

    {
        for(int i = 0; i < 10; i++){
          if (i < 10){ continue;}
        }
    }
}