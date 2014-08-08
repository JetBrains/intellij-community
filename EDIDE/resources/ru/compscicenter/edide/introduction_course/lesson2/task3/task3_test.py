from test_helper import run_common_tests, test_text_equals


if __name__ == '__main__':
    run_common_tests('''number = 9
print(type(number))

float_number = 9.0
print(float_number type)''', '''number = 9
print(type(number))

float_number = 9.0
print()''', "You should redefine variable 'greetings'")
    test_text_equals('''number = 9
print(type(number))

float_number = 9.0
print(type(float_number))''', "Use type() function.")
    # TODO: check only text in task window