import my.impl.MyServiceImpl;
import my.impl.MyServiceImpl1;
import my.impl.MyServiceImpl2;

module M {
    provides my.api.MyService with MyServiceImpl, MyServiceImpl2;
    provides my.api.MyService with <caret>MyServiceImpl1;
}