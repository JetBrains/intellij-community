// "Access static 'AClass.fff' via class 'AClass' reference|->Extract possible side effects" "true-preview"

class AClass
{
    AClass getA() {
        return null;
    }
    static int fff;
}

class acc {
 int f() {
   AClass a = null;
     a.getA();
     return AClass.fff;
 }
}