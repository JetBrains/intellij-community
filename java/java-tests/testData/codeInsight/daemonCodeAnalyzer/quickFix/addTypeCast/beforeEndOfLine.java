// "Cast expression to 'java.lang.Integer'" "true-preview"
class A {
    {
        Number n = 0;
        Integer i = n;<caret>
    }
}