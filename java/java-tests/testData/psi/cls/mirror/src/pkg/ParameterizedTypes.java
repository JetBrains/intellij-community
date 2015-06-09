package pkg;

public abstract class ParameterizedTypes<P> {

	public abstract class Inner<I> {		
	}
	
	abstract Inner<String> getUnspecificInner();
	
	abstract ParameterizedTypes<Number>.Inner<String> getSpecificInner();
	
}
