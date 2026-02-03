
interface Car {}
interface Ferrari extends Car {}
interface ICarRepairShop<C extends Car> {
    void repair(C car);
}
class AbstractCarRepairShop<C extends Car> implements ICarRepairShop<C> {
    @Override
    public void repair(Car car) { }
}

<error descr="'repair(C)' in 'ICarRepairShop' clashes with 'repair(Car)' in 'AbstractCarRepairShop'; both methods have same erasure, yet neither overrides the other">class FerrariRepairShop extends AbstractCarRepairShop<Ferrari></error> {
    @Override
    public void repair(Ferrari car) {
    }
}