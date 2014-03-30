interface A {
    void f(@Unknown1 @Unknown2 String s, @Unknown3 s3);
}
class B implements A {
    <caret>
}
