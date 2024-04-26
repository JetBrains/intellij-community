// "Qualify static 'stat' access with reference to class 'AClass'" "true-preview"

class AClass {
    static boolean stat;
    void foo (boolean stat) {
        AClass.stat<caret> = stat;
    }
}