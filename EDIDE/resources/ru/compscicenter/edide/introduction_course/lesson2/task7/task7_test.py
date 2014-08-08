from test_helper import run_common_tests, import_file, passed, failed


def test_value(path):
    file = import_file(path)
    if file.is_greater:
        passed()
    failed("Use > operator")



if __name__ == '__main__':
    run_common_tests('''one = 1
two = 2
three = 3

print(one < two < three)

is_greater = three operator two
print(is_greater)''', '''one = 1
two = 2
three = 3

print(one < two < three)

is_greater = three  two
print(is_greater)''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)