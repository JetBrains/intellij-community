class C {
    void foo() {
        for(int i = 0; i < 10; i++){
        <selection>if (i < 10){ continue;}</selection>
        }
    }

    {
        for(int i = 0; i < 10; i++){
          if (i < 10){ continue;}
        }
    }
}