// "Qualify static 'fff' access with reference to class 'AClass'|->Extract possible side effects" "true-preview"

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
   return <caret>a.getA().fff;
 }
}