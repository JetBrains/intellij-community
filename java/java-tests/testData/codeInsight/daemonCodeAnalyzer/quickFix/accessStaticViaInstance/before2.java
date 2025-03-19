// "Qualify static 'fff' access with reference to class 'AClass'" "true-preview"

class AClass
{
    AClass getA() {
        int i = <caret>this.fff;
        return null;

    }
    static int fff;
}

