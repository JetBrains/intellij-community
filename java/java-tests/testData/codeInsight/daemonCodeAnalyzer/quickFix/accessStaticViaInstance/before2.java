// "Access static 'AClass.fff' via class 'AClass' reference" "true-preview"

class AClass
{
    AClass getA() {
        int i = <caret>this.fff;
        return null;

    }
    static int fff;
}

