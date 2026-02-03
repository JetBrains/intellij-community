// "Convert to atomic" "true"
class Test {

    {
        int <caret>i = 0;
        Integer j = 0;

        assert j == i;
    }
}