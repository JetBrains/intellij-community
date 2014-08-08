from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('''phone_book = {"John": 123, "Jane": 234} # Empty dictionary
print(phone_book)

# Add new item to the dictionary
phone_book["Jill"] = 345
print(phone_book)

print(Jane's phone)

''',
                     '''phone_book = {"John": 123, "Jane": 234} # Empty dictionary
print(phone_book)

# Add new item to the dictionary
phone_book["Jill"] = 345
print(phone_book)

print()

''', "")
