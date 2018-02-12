import my.impl.MyServiceImpl;
import my.impl.MyServiceImpl1;
import my.impl.MyServiceImpl2;

module M {
    provides my.api.MyService with MyServiceImpl, MyServiceImpl1, MyServiceImpl2;<caret>
}