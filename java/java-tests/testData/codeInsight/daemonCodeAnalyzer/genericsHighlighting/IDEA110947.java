interface Result {}

interface Command<R extends Result> {}

interface Procedure<C extends Command<Result>> {
}

abstract class ProcedureService {
    abstract <C extends Command<Result>> Class<? extends Procedure<Command<Result>>> getProcedure(Class<C> cmd);

    public <C extends Command<Result>> void execute(Class<? extends Command> aClass) {
        Class<Procedure<Command<Result>>> procedureClass = getProcedure(aClass);
        <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Command>>', required: 'java.lang.Class<Command>'">Class<Command> c = aClass;</error>
        <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Command>>', required: 'java.lang.Class<C>'">Class<C> c1 = aClass;</error>
    }

}