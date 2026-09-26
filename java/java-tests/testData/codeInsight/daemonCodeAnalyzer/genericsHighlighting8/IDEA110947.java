interface Result {}

interface Command<R extends Result> {}

interface Procedure<C extends Command<Result>> {
}

abstract class ProcedureService {
    abstract <C extends Command<Result>> Class<? extends Procedure<Command<Result>>> getProcedure(Class<C> cmd);

    public <C extends Command<Result>> void execute(Class<? extends Command> aClass) {
        Class<Procedure<Command<Result>>> procedureClass = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Procedure<Command<Result>>>>', required: 'java.lang.Class<Procedure<Command<Result>>>'">getProcedure</error>(aClass);
        Class<Command> c = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Command>>', required: 'java.lang.Class<Command>'">aClass</error>;
        Class<C> c1 = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Command>>', required: 'java.lang.Class<C>'">aClass</error>;
    }

}
