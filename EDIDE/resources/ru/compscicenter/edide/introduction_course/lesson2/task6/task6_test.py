from test_helper import run_common_tests, import_file, failed, passed


def test_value(path):
    file = import_file(path)
    if not file.is_equal:
        passed()
    failed("Use == operator")



if __name__ == '__main__':
    run_common_tests('''two = 2
three = 3

is_equal = two operator three

print(is_equal)''', '''two = 2
three = 3

is_equal = two  three

print(is_equal)''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)

    #TODO check exact value in task window