import java.util.List;

@interface PrepareForTest {
    Class<List<String[]>> value();
}

abstract class A implements List<String>{}

@PrepareForTest({A<caret>.class})
class Main {}
