public class MyAspect {

    public void foo() {
        State state;
        StateListener stateListener;
        state.addListener( sta<caret>);

    }

    private static class State {
        void addListener(StateListener listener) {}
    }



    private static class StateListener {

    }

}
