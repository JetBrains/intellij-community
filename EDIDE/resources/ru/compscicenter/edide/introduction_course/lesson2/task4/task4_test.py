from test_helper import run_common_tests, import_file, passed, failed


def test_division(path):
    file = import_file(path)
    if file.division == 4.5:
        passed()
    failed("Use / operator")

def test_remainder(path):
    file = import_file(path)
    if file.remainder == 1.0:
        passed()
    failed("Use % operator")


if __name__ == '__main__':
    run_common_tests('''number = 9.0

division = divide 'number' by two

remainder = calculate the remainder

print("division = " + str(division))
print("remainder = " + str(remainder))''', '''number = 9.0

division =

remainder =

print("division = " + str(division))
print("remainder = " + str(remainder))''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_division(path)
    test_remainder(path)