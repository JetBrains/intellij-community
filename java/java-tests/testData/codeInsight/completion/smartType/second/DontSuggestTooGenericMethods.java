interface Zip extends Bar{}

interface Bar {}

interface Foo {
    <T> T any();
    <T extends Goo> T gooAny();
    <T extends Zip> T zipAny();
    <T extends Bar> T barAny();

}

class Goo {
    {
        Foo f;
        Bar a = f<caret>
    }
}

