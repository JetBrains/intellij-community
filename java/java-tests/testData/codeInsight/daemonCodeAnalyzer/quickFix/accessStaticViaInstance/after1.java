// "Access static 'AClass.fff' via class 'AClass' reference" "true"

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
   return <caret>AClass.fff;
 }
}