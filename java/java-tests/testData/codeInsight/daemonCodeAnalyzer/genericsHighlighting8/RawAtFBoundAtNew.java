class InvalidGenericSubclass {
  public static void main(String[] args) {
    BaseInput<?, ?> input = new BaseInput<<error descr="Type parameter 'InvalidGenericSubclass.BaseInput' is not within its bound; should extend 'InvalidGenericSubclass.BaseInput<InvalidGenericSubclass.BaseInput,InvalidGenericSubclass.BaseInput.BaseOutput>'">BaseInput</error>, BaseInput.BaseOutput>() {
    };

    BaseInput<?, ?> input2 = InvalidGenericSubclass.<BaseInput, BaseInput.BaseOutput>create();

    new <BaseInput, BaseInput.BaseOutput>InvalidGenericSubclass();
  }

  interface Input<InputType extends Input<InputType, OutputType>, OutputType extends Input.Output<InputType, OutputType>> {
    interface Output<OutputType extends Input<OutputType, InputType>, InputType extends Output<OutputType, InputType>> {
    }
  }

  interface BaseInput<BaseInputType extends BaseInput<BaseInputType, BaseOutputType>, BaseOutputType extends BaseInput.BaseOutput<BaseInputType, BaseOutputType>> extends Input<BaseInputType, BaseOutputType> {
    interface BaseOutput<BaseInputType extends BaseInput<BaseInputType, BaseOutputType>, BaseOutputType extends BaseOutput<BaseInputType, BaseOutputType>> extends Output<BaseInputType, BaseOutputType> {
    }
  }

  static <BaseInputType extends BaseInput<BaseInputType, BaseOutputType>, BaseOutputType extends BaseInput.BaseOutput<BaseInputType, BaseOutputType>> BaseInput<BaseInputType, BaseOutputType> create() {
    return null;
  }

  <BaseInputType extends BaseInput<BaseInputType, BaseOutputType>, BaseOutputType extends BaseInput.BaseOutput<BaseInputType, BaseOutputType>> InvalidGenericSubclass() {}
}