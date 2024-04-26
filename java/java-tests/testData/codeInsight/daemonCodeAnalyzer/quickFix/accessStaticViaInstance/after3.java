// "Qualify static 'getA()' call with reference to class 'AClass'" "true-preview"

class AClass
{
    static AClass getA() {
        return null;
    }
    static int fff;
}

class acc {
 int f() {
   AClass a = null;
   <caret>AClass.getA();
   return 0;
 }
}