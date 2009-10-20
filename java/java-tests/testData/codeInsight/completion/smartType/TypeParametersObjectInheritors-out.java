class CharSequenceX {}

class Stringy<S> { }

class Tester {
void method() { new Stringy<CharSequenceX><caret> }