class Test {

    public static void main(String[] args) {
        MyCar myCar=new MyCar();
        myCar.get<caret>
    }

}

class MyCar<C extends MyDoor> extends AbstractCar<C> implements Car{ }

abstract class AbstractCar<C extends Door> {
    public C get() {}
}

interface Car {
    public CarDoor get();
}

interface MyDoor extends CarDoor{}

interface CarDoor extends Door{}

interface Door {}