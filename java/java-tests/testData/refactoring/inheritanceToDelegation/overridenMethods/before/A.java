class A extends Base {    
    int fieldFromA;
    public void firstMethodFromBase() {
        super.firstMethodFromBase();
    }
    public void secondMethodFromBase() {
        fieldFromA = 27;
        this.fieldFromA++;
    }
    Base getInstance() {
        return this;
    }
}